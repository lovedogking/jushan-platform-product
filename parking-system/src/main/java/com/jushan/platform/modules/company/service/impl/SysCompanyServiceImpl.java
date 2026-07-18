package com.jushan.platform.modules.company.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.company.dto.CompanyCreateCmd;
import com.jushan.platform.modules.company.dto.CompanyUpdateCmd;
import com.jushan.platform.modules.company.entity.SysCompany;
import com.jushan.platform.modules.company.mapper.SysCompanyMapper;
import com.jushan.platform.modules.company.service.SysCompanyService;
import com.jushan.platform.modules.company.vo.CompanyTreeVO;
import com.jushan.platform.modules.company.vo.CompanyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 公司/集团档案服务实现。
 * <p>
 * 实现公司 CRUD、树形查询及删除前校验。
 * 所有操作基于 {@link TenantContext} 推导租户范围，不信任前端传入的租户ID。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class SysCompanyServiceImpl extends ServiceImpl<SysCompanyMapper, SysCompany> implements SysCompanyService {

    /** 集团级别 */
    private static final int LEVEL_GROUP = 1;
    /** 子公司级别 */
    private static final int LEVEL_SUBSIDIARY = 2;
    /** 分公司级别 */
    private static final int LEVEL_BRANCH = 3;
    /** 顶级节点父ID */
    private static final Long TOP_PARENT_ID = 0L;

    /**
     * 解析当前租户ID，正确处理平台用户。
     * <p>
     * 平台用户（super_admin、platform_operator）无租户绑定，返回 null。
     * 租户用户返回其 tenantId，未登录或上下文异常时抛出异常。
     *
     * @return 租户ID（平台用户返回 null）
     */
    private Long resolveTenantId() {
        if (TenantContext.isPlatformUser()) {
            return null;
        }
        return TenantContext.requireTenantId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyVO create(CompanyCreateCmd cmd) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            // 平台用户：从请求体获取目标租户 ID
            tenantId = cmd.getTenantId();
            if (tenantId == null) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "平台用户需指定租户ID后创建公司");
            }
        }
        validateNameUnique(tenantId, cmd.getName(), null);

        SysCompany company = new SysCompany();
        company.setTenantId(tenantId);
        company.setParentId(cmd.getParentId());
        company.setName(cmd.getName().trim());
        company.setCode(cmd.getCode());
        company.setLevel(cmd.getLevel());
        company.setContactName(cmd.getContactName());
        company.setContactPhone(cmd.getContactPhone());
        company.setAddress(cmd.getAddress());
        company.setSortOrder(cmd.getSortOrder());
        company.setUpdatedAt(LocalDateTime.now());

        validateParentAndLevel(company, null);

        baseMapper.insert(company);
        log.info("创建公司成功: tenantId={}, companyId={}, name={}", tenantId, company.getId(), company.getName());
        return toVO(company);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompanyVO updateCompany(Long id, CompanyUpdateCmd cmd) {
        Long tenantId = resolveTenantId();
        SysCompany company = getAndCheck(id, tenantId);
        // 平台用户使用公司自身的 tenantId 进行数据校验
        Long effectiveTenantId = tenantId != null ? tenantId : company.getTenantId();

        String newName = cmd.getName().trim();
        if (!Objects.equals(newName, company.getName())) {
            validateNameUnique(effectiveTenantId, newName, id);
        }

        company.setParentId(cmd.getParentId());
        company.setName(newName);
        company.setCode(cmd.getCode());
        company.setLevel(cmd.getLevel());
        company.setContactName(cmd.getContactName());
        company.setContactPhone(cmd.getContactPhone());
        company.setAddress(cmd.getAddress());
        company.setSortOrder(cmd.getSortOrder());
        company.setUpdatedAt(LocalDateTime.now());

        validateParentAndLevel(company, id);

        baseMapper.updateById(company);
        log.info("更新公司成功: tenantId={}, companyId={}", tenantId, id);
        return toVO(company);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCompany(Long id) {
        Long tenantId = resolveTenantId();
        SysCompany company = getAndCheck(id, tenantId);
        // 平台用户使用公司自身的 tenantId 进行子节点检查
        Long effectiveTenantId = tenantId != null ? tenantId : company.getTenantId();

        // 1. 检查是否存在未删除的子节点
        long childCount = baseMapper.selectCount(
                new LambdaQueryWrapper<SysCompany>()
                        .eq(SysCompany::getTenantId, effectiveTenantId)
                        .eq(SysCompany::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该公司存在下级公司，请先删除下级公司");
        }

        // 2. 检查是否存在关联的管理员账号
        long accountCount = baseMapper.countAdminAccountByCompanyId(id);
        if (accountCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该公司下存在关联管理员账号，无法删除");
        }

        // 3. 软删除
        removeById(id);
        log.info("删除公司成功: tenantId={}, companyId={}", tenantId, id);
    }

    @Override
    public CompanyVO detail(Long id) {
        Long tenantId = resolveTenantId();
        return toVO(getAndCheck(id, tenantId));
    }

    @Override
    public List<CompanyTreeVO> tree() {
        Long tenantId = resolveTenantId();
        List<SysCompany> companies;
        if (tenantId == null) {
            // 平台用户：查看所有租户的公司树（不按 tenant_id 过滤）
            companies = list();
        } else {
            companies = baseMapper.selectTreeByTenantId(tenantId);
        }
        return buildTree(companies);
    }

    @Override
    public IPage<CompanyVO> pageList(IPage<SysCompany> page, String name, Integer level, Long parentId) {
        Long tenantId = resolveTenantId();
        LambdaQueryWrapper<SysCompany> wrapper = new LambdaQueryWrapper<SysCompany>()
                .eq(tenantId != null, SysCompany::getTenantId, tenantId)
                .orderByAsc(SysCompany::getSortOrder)
                .orderByDesc(SysCompany::getCreatedAt);

        if (name != null && !name.isBlank()) {
            wrapper.like(SysCompany::getName, name.trim());
        }
        if (level != null) {
            wrapper.eq(SysCompany::getLevel, level);
        }
        if (parentId != null) {
            wrapper.eq(SysCompany::getParentId, parentId);
        }

        IPage<SysCompany> entityPage = baseMapper.selectPage(page, wrapper);
        List<CompanyVO> records = entityPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        Page<CompanyVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    /**
     * 根据ID查询公司并校验租户归属。
     *
     * @param id       公司ID
     * @param tenantId 当前租户ID（平台用户为 null）
     * @return 公司实体
     */
    private SysCompany getAndCheck(Long id, Long tenantId) {
        SysCompany company = baseMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "公司不存在");
        }
        // 平台用户可访问所有租户的公司数据
        if (tenantId == null) {
            return company;
        }
        if (!Objects.equals(tenantId, company.getTenantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问其他租户数据");
        }
        return company;
    }

    /**
     * 校验同一租户内公司名称唯一性。
     *
     * @param tenantId 租户ID
     * @param name     公司名称
     * @param excludeId 排除的公司ID（编辑时排除自身）
     */
    private void validateNameUnique(Long tenantId, String name, Long excludeId) {
        LambdaQueryWrapper<SysCompany> wrapper = new LambdaQueryWrapper<SysCompany>()
                .eq(SysCompany::getTenantId, tenantId)
                .eq(SysCompany::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(SysCompany::getId, excludeId);
        }
        long count = baseMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "同一租户内公司名称已存在");
        }
    }

    /**
     * 校验上级公司合法性及级别匹配关系。
     * <p>
     * 规则：
     * <ul>
     *   <li>顶级节点父ID必须为0，级别必须为集团（1）</li>
     *   <li>不能选择自己作为上级</li>
     *   <li>不能选择自己的子节点作为上级（防止循环引用）</li>
     *   <li>下级级别必须等于上级级别加1，且不能超过3级</li>
     * </ul>
     *
     * @param company  待校验的公司实体
     * @param currentId 当前编辑的公司ID（新增时为null）
     */
    private void validateParentAndLevel(SysCompany company, Long currentId) {
        Long tenantId = company.getTenantId();
        Long parentId = company.getParentId();
        Integer level = company.getLevel();

        // 未指定或小于等于0视为顶级节点
        if (parentId == null || parentId <= 0) {
            company.setParentId(TOP_PARENT_ID);
            if (level != LEVEL_GROUP && level != LEVEL_SUBSIDIARY) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR, "顶级节点级别必须为集团（1）或公司（2）");
            }
            return;
        }

        // 不能选择自己作为上级
        if (currentId != null && Objects.equals(parentId, currentId)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "不能选择自己作为上级");
        }

        // 上级公司必须存在且属于当前租户
        SysCompany parent = baseMapper.selectById(parentId);
        if (parent == null || !Objects.equals(tenantId, parent.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "上级公司不存在");
        }

        // 不能选择自己的子节点作为上级（防止循环引用）
        if (currentId != null && isDescendant(currentId, parentId, tenantId)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "不能选择自己的子节点作为上级");
        }

        // 级别必须比上级大1
        int expectedLevel = parent.getLevel() + 1;
        if (expectedLevel > LEVEL_BRANCH) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "公司级别不能超过3级");
        }
        if (!Integer.valueOf(expectedLevel).equals(level)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "公司级别与上级不匹配，应为：" + expectedLevel);
        }
    }

    /**
     * 判断 targetId 是否为 ancestorId 的后代节点。
     *
     * @param ancestorId 祖先节点ID
     * @param targetId   目标节点ID
     * @param tenantId   租户ID
     * @return true 表示 targetId 是 ancestorId 的后代
     */
    private boolean isDescendant(Long ancestorId, Long targetId, Long tenantId) {
        List<SysCompany> allCompanies = baseMapper.selectList(
                new LambdaQueryWrapper<SysCompany>()
                        .eq(SysCompany::getTenantId, tenantId));
        Map<Long, Long> parentMap = allCompanies.stream()
                .collect(Collectors.toMap(SysCompany::getId,
                        c -> c.getParentId() == null ? TOP_PARENT_ID : c.getParentId()));

        Long current = targetId;
        while (current != null && !TOP_PARENT_ID.equals(current)) {
            Long parent = parentMap.get(current);
            if (parent == null) {
                return false;
            }
            if (Objects.equals(parent, ancestorId)) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    /**
     * 将扁平公司列表构建为树形结构。
     *
     * @param companies 公司列表
     * @return 树形列表
     */
    private List<CompanyTreeVO> buildTree(List<SysCompany> companies) {
        if (companies == null || companies.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CompanyTreeVO> nodeMap = companies.stream()
                .map(this::toTreeVO)
                .collect(Collectors.toMap(CompanyTreeVO::getId, v -> v));

        List<CompanyTreeVO> roots = new ArrayList<>();
        for (SysCompany company : companies) {
            CompanyTreeVO node = nodeMap.get(company.getId());
            Long parentId = company.getParentId();
            if (parentId == null || parentId <= 0 || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                CompanyTreeVO parent = nodeMap.get(parentId);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 实体转视图对象。
     *
     * @param company 公司实体
     * @return 公司视图对象
     */
    private CompanyVO toVO(SysCompany company) {
        CompanyVO vo = new CompanyVO();
        vo.setId(company.getId());
        vo.setTenantId(company.getTenantId());
        vo.setParentId(company.getParentId());
        vo.setName(company.getName());
        vo.setCode(company.getCode());
        vo.setLevel(company.getLevel());
        vo.setContactName(company.getContactName());
        vo.setContactPhone(company.getContactPhone());
        vo.setAddress(company.getAddress());
        vo.setSortOrder(company.getSortOrder());
        vo.setCreatedAt(company.getCreatedAt());
        vo.setUpdatedAt(company.getUpdatedAt());
        return vo;
    }

    /**
     * 实体转树形视图对象。
     *
     * @param company 公司实体
     * @return 公司树形视图对象
     */
    private CompanyTreeVO toTreeVO(SysCompany company) {
        CompanyTreeVO vo = new CompanyTreeVO();
        vo.setId(company.getId());
        vo.setTenantId(company.getTenantId());
        vo.setParentId(company.getParentId());
        vo.setName(company.getName());
        vo.setCode(company.getCode());
        vo.setLevel(company.getLevel());
        vo.setContactName(company.getContactName());
        vo.setContactPhone(company.getContactPhone());
        vo.setAddress(company.getAddress());
        vo.setSortOrder(company.getSortOrder());
        vo.setCreatedAt(company.getCreatedAt());
        vo.setUpdatedAt(company.getUpdatedAt());
        return vo;
    }
}
