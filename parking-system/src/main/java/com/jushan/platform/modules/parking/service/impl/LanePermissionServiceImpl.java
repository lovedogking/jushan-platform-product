package com.jushan.platform.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.dto.LanePermissionCreateCmd;
import com.jushan.platform.modules.parking.dto.LanePermissionUpdateCmd;
import com.jushan.platform.modules.parking.entity.LanePermission;
import com.jushan.platform.modules.parking.mapper.LanePermissionMapper;
import com.jushan.platform.modules.parking.service.LanePermissionService;
import com.jushan.platform.modules.parking.vo.LanePermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通道权限配置服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class LanePermissionServiceImpl extends ServiceImpl<LanePermissionMapper, LanePermission>
        implements LanePermissionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LanePermissionVO create(LanePermissionCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        // 校验是否已存在相同权限
        LambdaQueryWrapper<LanePermission> checkWrapper = new LambdaQueryWrapper<LanePermission>()
                .eq(LanePermission::getTenantId, tenantId)
                .eq(LanePermission::getLaneId, cmd.getLaneId())
                .eq(LanePermission::getTargetType, cmd.getTargetType())
                .eq(LanePermission::getTargetId, cmd.getTargetId())
                .isNull(LanePermission::getDeletedAt);

        long count = baseMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该通道已存在相同的权限配置");
        }

        LanePermission entity = new LanePermission();
        BeanUtils.copyProperties(cmd, entity);
        entity.setTenantId(tenantId);
        entity.setStatus(LanePermission.STATUS_ACTIVE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("创建通道权限成功: permissionId={}, laneId={}, targetType={}, targetId={}",
                entity.getId(), cmd.getLaneId(), cmd.getTargetType(), cmd.getTargetId());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LanePermissionVO update(Long id, LanePermissionUpdateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        LanePermission entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "权限配置不存在");
        }

        BeanUtils.copyProperties(cmd, entity);
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("更新通道权限成功: permissionId={}", id);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();

        LanePermission entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "权限配置不存在");
        }

        baseMapper.deleteById(id);
        log.info("删除通道权限成功: permissionId={}", id);
    }

    @Override
    public LanePermissionVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        LanePermission entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "权限配置不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<LanePermissionVO> pageList(IPage<LanePermission> page, Long laneId, String targetType, Long targetId, String status) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<LanePermission> wrapper = new LambdaQueryWrapper<LanePermission>()
                .eq(LanePermission::getTenantId, tenantId)
                .eq(laneId != null, LanePermission::getLaneId, laneId)
                .eq(targetType != null && !targetType.isEmpty(), LanePermission::getTargetType, targetType)
                .eq(targetId != null, LanePermission::getTargetId, targetId)
                .eq(status != null && !status.isEmpty(), LanePermission::getStatus, status)
                .isNull(LanePermission::getDeletedAt)
                .orderByDesc(LanePermission::getCreatedAt);

        IPage<LanePermission> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    public List<LanePermissionVO> listByLaneId(Long laneId) {
        Long tenantId = TenantContext.getTenantId();
        List<LanePermission> list = baseMapper.selectByLaneId(laneId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<LanePermissionVO> listByVehicleId(Long vehicleId) {
        Long tenantId = TenantContext.getTenantId();
        List<LanePermission> list = baseMapper.selectByVehicleId(vehicleId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<LanePermissionVO> listByDepartmentId(Long departmentId) {
        Long tenantId = TenantContext.getTenantId();
        List<LanePermission> list = baseMapper.selectByDepartmentId(departmentId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    private LanePermissionVO toVO(LanePermission entity) {
        LanePermissionVO vo = new LanePermissionVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
