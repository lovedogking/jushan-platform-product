package com.jushan.platform.modules.department.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.department.dto.DepartmentCreateCmd;
import com.jushan.platform.modules.department.dto.DepartmentUpdateCmd;
import com.jushan.platform.modules.department.entity.SysDepartment;
import com.jushan.platform.modules.department.mapper.SysDepartmentMapper;
import com.jushan.platform.modules.department.service.SysDepartmentService;
import com.jushan.platform.modules.department.vo.DepartmentTreeVO;
import com.jushan.platform.modules.department.vo.DepartmentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门/组织架构服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class SysDepartmentServiceImpl extends ServiceImpl<SysDepartmentMapper, SysDepartment> implements SysDepartmentService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO create(DepartmentCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        SysDepartment entity = new SysDepartment();
        BeanUtils.copyProperties(cmd, entity);
        entity.setTenantId(tenantId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("新增部门成功: departmentId={}, name={}, tenantId={}", entity.getId(), entity.getName(), tenantId);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO updateDepartment(Long id, DepartmentUpdateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        SysDepartment entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "部门不存在");
        }

        BeanUtils.copyProperties(cmd, entity);
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("编辑部门成功: departmentId={}", id);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartment(Long id) {
        Long tenantId = TenantContext.getTenantId();

        SysDepartment entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "部门不存在");
        }

        // 校验是否有子部门
        long childrenCount = baseMapper.countChildren(id);
        if (childrenCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "存在子部门，无法删除");
        }

        // TODO: 校验是否有关联车辆（TASK-0402 完成后实现）

        baseMapper.deleteById(id);
        log.info("删除部门成功: departmentId={}", id);
    }

    @Override
    public DepartmentVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        SysDepartment entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "部门不存在");
        }

        return toVO(entity);
    }

    @Override
    public List<DepartmentTreeVO> tree() {
        Long tenantId = TenantContext.getTenantId();

        List<SysDepartment> list = baseMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>()
                        .eq(SysDepartment::getTenantId, tenantId)
                        .orderByAsc(SysDepartment::getSortOrder, SysDepartment::getId)
        );

        return buildTree(list);
    }

    @Override
    public IPage<DepartmentVO> pageList(IPage<SysDepartment> page, String name, Long parkingLotId, Long parentId) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<SysDepartment> wrapper = new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getTenantId, tenantId);

        if (name != null && !name.isEmpty()) {
            wrapper.like(SysDepartment::getName, name);
        }
        if (parkingLotId != null) {
            wrapper.eq(SysDepartment::getParkingLotId, parkingLotId);
        }
        if (parentId != null) {
            wrapper.eq(SysDepartment::getParentId, parentId);
        }

        wrapper.orderByAsc(SysDepartment::getSortOrder, SysDepartment::getId);

        IPage<SysDepartment> entityPage = baseMapper.selectPage(page, wrapper);

        return entityPage.convert(this::toVO);
    }

    private DepartmentVO toVO(SysDepartment entity) {
        DepartmentVO vo = new DepartmentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private List<DepartmentTreeVO> buildTree(List<SysDepartment> list) {
        List<DepartmentTreeVO> all = list.stream().map(this::toTreeVO).collect(Collectors.toList());

        List<DepartmentTreeVO> roots = new ArrayList<>();
        for (DepartmentTreeVO node : all) {
            if (node.getParentId() == null || node.getParentId() == 0L) {
                roots.add(node);
            }
        }

        for (DepartmentTreeVO root : roots) {
            buildChildren(root, all);
        }

        return roots;
    }

    private void buildChildren(DepartmentTreeVO parent, List<DepartmentTreeVO> all) {
        List<DepartmentTreeVO> children = new ArrayList<>();
        for (DepartmentTreeVO node : all) {
            if (parent.getId().equals(node.getParentId())) {
                children.add(node);
                buildChildren(node, all);
            }
        }
        parent.setChildren(children);
    }

    private DepartmentTreeVO toTreeVO(SysDepartment entity) {
        DepartmentTreeVO vo = new DepartmentTreeVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setName(entity.getName());
        vo.setCode(entity.getCode());
        vo.setLevel(entity.getLevel());
        vo.setSortOrder(entity.getSortOrder());
        return vo;
    }
}
