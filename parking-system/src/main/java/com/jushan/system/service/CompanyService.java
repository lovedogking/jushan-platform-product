package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.dto.CreateCompanyRequest;
import com.jushan.system.dto.UpdateCompanyRequest;
import com.jushan.system.entity.Company;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.mapper.CompanyMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.vo.CompanyTreeVO;
import com.jushan.system.vo.CompanyVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 公司/集团档案服务。
 * <p>
 * 负责公司 CRUD、树形结构维护与删除前校验。
 * 平台用户（super_admin）可跨租户查看与操作公司数据。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    /** 公司级别：集团 */
    public static final int LEVEL_GROUP = 1;
    /** 公司级别：子公司 */
    public static final int LEVEL_SUBSIDIARY = 2;
    /** 公司级别：分公司 */
    public static final int LEVEL_BRANCH = 3;

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;
    /** 状态：暂停 */
    public static final int STATUS_SUSPENDED = 2;
    /** 状态：注销 */
    public static final int STATUS_CANCELLED = 3;

    private final CompanyMapper companyMapper;
    private final ParkingLotMapper parkingLotMapper;

    public CompanyService(CompanyMapper companyMapper, ParkingLotMapper parkingLotMapper) {
        this.companyMapper = companyMapper;
        this.parkingLotMapper = parkingLotMapper;
    }

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
        return DataScope.requireTenantUser();
    }

    // ==================== 创建公司 ====================

    /**
     * 创建公司/集团。
     * <p>
     * 校验：
     * <ul>
     *   <li>租户内名称唯一（未删除）</li>
     *   <li>上级公司存在且属于本租户</li>
     *   <li>level 与 parent level 的级次关系合法（只能比上级大 1）</li>
     * </ul>
     */
    @Transactional
    public CompanyVO create(CreateCompanyRequest request) {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "平台用户请通过代操作模式进入目标租户后创建公司");
        }

        String name = request.getName().trim();
        validateNameUnique(tenantId, name, null);

        Integer level = request.getLevel() != null ? request.getLevel() : LEVEL_GROUP;
        Long parentId = request.getParentId();
        String parentPath = "/";

        if (parentId != null) {
            Company parent = getCompanyById(parentId);
            if (parent.getLevel() >= LEVEL_BRANCH) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "分公司下不能再创建下级公司");
            }
            int expectedLevel = parent.getLevel() + 1;
            if (level != expectedLevel) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "公司级别与上级不匹配，应为: " + expectedLevel);
            }
            parentPath = parent.getPath();
        } else if (level != LEVEL_GROUP) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "顶级公司级别必须为集团（1）");
        }

        Company company = new Company();
        company.setTenantId(tenantId);
        company.setParentId(parentId);
        company.setName(name);
        company.setLevel(level);
        company.setStatus(STATUS_NORMAL);
        company.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        company.setContactName(request.getContactName());
        company.setContactPhone(request.getContactPhone());
        // path 在 insert 后根据 id 回填
        company.setPath("");

        companyMapper.insert(company);

        // 生成 path：上级 path + 当前 id + /
        String path = parentPath + company.getId() + "/";
        company.setPath(path);
        companyMapper.updateById(company);

        log.info("创建公司成功: tenantId={}, companyId={}, name={}, level={}",
                tenantId, company.getId(), name, level);
        return toVO(company);
    }

    // ==================== 更新公司 ====================

    /**
     * 更新公司信息。
     * <p>
     * 支持修改名称、上级、级别、状态、排序、联系人等。
     * 关键约束：
     * <ul>
     *   <li>不能将自己设为自己的上级</li>
     *   <li>不能将上级设为自己的后代（防止环）</li>
     *   <li>修改上级时 level 必须与新上级的级次匹配</li>
     * </ul>
     */
    @Transactional
    public CompanyVO update(Long companyId, UpdateCompanyRequest request) {
        Long tenantId = resolveTenantId();
        Company company = getCompanyById(companyId);
        Long effectiveTenantId = tenantId != null ? tenantId : company.getTenantId();

        boolean pathChanged = false;
        String oldPath = company.getPath();
        String newParentPath = "/";

        // 名称变更
        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            if (!newName.equals(company.getName())) {
                validateNameUnique(effectiveTenantId, newName, companyId);
                company.setName(newName);
            }
        }

        // 上级变更或级别变更
        Long newParentId = request.getParentId() != null ? request.getParentId() : company.getParentId();
        Integer newLevel = request.getLevel() != null ? request.getLevel() : company.getLevel();

        if (Objects.equals(newParentId, companyId)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "不能将自己设为自己的上级");
        }

        if (newParentId != null) {
            Company parent = getCompanyById(newParentId);
            if (parent.getLevel() >= LEVEL_BRANCH) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "分公司下不能再创建下级公司");
            }
            int expectedLevel = parent.getLevel() + 1;
            if (newLevel != expectedLevel) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "公司级别与上级不匹配，应为: " + expectedLevel);
            }
            // 防止把上级设为自己的后代
            if (parent.getPath().startsWith(oldPath)) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "不能将上级设为自己的后代公司");
            }
            newParentPath = parent.getPath();
        } else {
            if (newLevel != LEVEL_GROUP) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "顶级公司级别必须为集团（1）");
            }
        }

        if (!Objects.equals(newParentId, company.getParentId()) || !newLevel.equals(company.getLevel())) {
            company.setParentId(newParentId);
            company.setLevel(newLevel);
            pathChanged = true;
        }

        if (request.getStatus() != null) {
            company.setStatus(request.getStatus());
        }
        if (request.getSortOrder() != null) {
            company.setSortOrder(request.getSortOrder());
        }
        if (request.getContactName() != null) {
            company.setContactName(request.getContactName());
        }
        if (request.getContactPhone() != null) {
            company.setContactPhone(request.getContactPhone());
        }

        if (pathChanged) {
            // 更新当前节点及所有后代节点的 path
            String newPath = newParentPath + company.getId() + "/";
            updateDescendantPaths(oldPath, newPath);
            company.setPath(newPath);
        }

        company.setUpdatedAt(LocalDateTime.now());
        companyMapper.updateById(company);

        log.info("更新公司成功: tenantId={}, companyId={}", tenantId, companyId);
        return toVO(company);
    }

    // ==================== 删除公司 ====================

    /**
     * 软删除公司。
     * <p>
     * 删除前校验：
     * <ul>
     *   <li>不存在未删除的子节点</li>
     *   <li>不存在关联的停车场</li>
     * </ul>
     */
    @Transactional
    public void delete(Long companyId) {
        Long tenantId = resolveTenantId();
        Company company = getCompanyById(companyId);
        Long effectiveTenantId = tenantId != null ? tenantId : company.getTenantId();

        // 1. 检查子节点
        long childCount = companyMapper.selectCount(
                new LambdaQueryWrapper<Company>()
                        .eq(Company::getParentId, companyId)
                        .isNull(Company::getDeletedAt));
        if (childCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该公司存在下级公司，请先删除下级公司");
        }

        // 2. 检查关联车场
        long lotCount = parkingLotMapper.selectCount(
                new LambdaQueryWrapper<ParkingLot>()
                        .eq(ParkingLot::getCompanyId, companyId));
        if (lotCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该公司下存在关联停车场，无法删除");
        }

        // 3. 软删除
        company.setDeletedAt(LocalDateTime.now());
        company.setUpdatedAt(LocalDateTime.now());
        companyMapper.updateById(company);

        log.info("删除公司成功: tenantId={}, companyId={}", tenantId, companyId);
    }

    // ==================== 查询 ====================

    /**
     * 查询公司详情。
     * <p>
     * 平台用户可查看所有租户的公司详情。
     */
    public CompanyVO get(Long companyId) {
        // getCompanyById 内部通过 DataScope.validateTenantMatch 处理平台用户
        Company company = getCompanyById(companyId);
        return toVO(company);
    }

    /**
     * 查询完整公司树。
     * <p>
     * 平台用户查看所有租户的公司树，租户用户仅查看本租户。
     *
     * @return 顶层集团列表，每个节点递归包含子节点
     */
    public List<CompanyTreeVO> tree() {
        Long tenantId = resolveTenantId();

        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(Company::getTenantId, tenantId);
        }
        wrapper.isNull(Company::getDeletedAt)
               .orderByAsc(Company::getPath)
               .last(", sort_order ASC");

        List<Company> companies = companyMapper.selectList(wrapper);
        return buildTree(companies);
    }

    // ==================== 内部方法 ====================

    /**
     * 根据 ID 查询未删除的公司，并校验租户归属。
     */
    private Company getCompanyById(Long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "公司不存在");
        }
        DataScope.validateTenantMatch(company.getTenantId(), "公司");
        return company;
    }

    /**
     * 校验租户内公司名称唯一（排除自身）。
     */
    private void validateNameUnique(Long tenantId, String name, Long excludeId) {
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                .eq(Company::getTenantId, tenantId)
                .eq(Company::getName, name)
                .isNull(Company::getDeletedAt);
        if (excludeId != null) {
            wrapper.ne(Company::getId, excludeId);
        }
        long count = companyMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "租户内已存在同名公司");
        }
    }

    /**
     * 批量更新后代节点的 path。
     *
     * @param oldPrefix 旧 path 前缀
     * @param newPrefix 新 path 前缀
     */
    private void updateDescendantPaths(String oldPrefix, String newPrefix) {
        if (oldPrefix.equals(newPrefix)) {
            return;
        }
        List<Company> descendants = companyMapper.selectDescendantsByPath(oldPrefix);
        for (Company descendant : descendants) {
            if (descendant.getPath().equals(oldPrefix)) {
                // 当前节点自身在 updateById 中已处理
                continue;
            }
            String updatedPath = descendant.getPath().replaceFirst(
                    java.util.regex.Pattern.quote(oldPrefix), newPrefix);
            descendant.setPath(updatedPath);
            descendant.setUpdatedAt(LocalDateTime.now());
            companyMapper.updateById(descendant);
        }
    }

    /**
     * 将扁平列表构建为树形结构。
     */
    private List<CompanyTreeVO> buildTree(List<Company> companies) {
        if (companies.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CompanyTreeVO> nodeMap = companies.stream()
                .collect(Collectors.toMap(Company::getId, this::toTreeVO));

        List<CompanyTreeVO> roots = new ArrayList<>();
        for (Company company : companies) {
            CompanyTreeVO node = nodeMap.get(company.getId());
            if (company.getParentId() == null || !nodeMap.containsKey(company.getParentId())) {
                roots.add(node);
            } else {
                CompanyTreeVO parent = nodeMap.get(company.getParentId());
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private CompanyVO toVO(Company company) {
        CompanyVO vo = new CompanyVO();
        vo.setId(company.getId());
        vo.setParentId(company.getParentId());
        vo.setName(company.getName());
        vo.setLevel(company.getLevel());
        vo.setStatus(company.getStatus());
        vo.setSortOrder(company.getSortOrder());
        vo.setPath(company.getPath());
        vo.setContactName(company.getContactName());
        vo.setContactPhone(company.getContactPhone());
        vo.setCreatedAt(company.getCreatedAt());
        vo.setUpdatedAt(company.getUpdatedAt());
        return vo;
    }

    private CompanyTreeVO toTreeVO(Company company) {
        CompanyTreeVO vo = new CompanyTreeVO();
        vo.setId(company.getId());
        vo.setParentId(company.getParentId());
        vo.setName(company.getName());
        vo.setLevel(company.getLevel());
        vo.setStatus(company.getStatus());
        vo.setSortOrder(company.getSortOrder());
        vo.setPath(company.getPath());
        vo.setContactName(company.getContactName());
        vo.setContactPhone(company.getContactPhone());
        return vo;
    }
}
