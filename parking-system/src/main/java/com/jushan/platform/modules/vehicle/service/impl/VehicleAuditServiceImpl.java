package com.jushan.platform.modules.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.dto.VehicleAuditProcessCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleAuditSubmitCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.entity.VehicleAudit;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.VehicleAuditMapper;
import com.jushan.platform.modules.vehicle.service.VehicleAuditService;
import com.jushan.platform.modules.vehicle.vo.VehicleAuditVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 车辆审核服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class VehicleAuditServiceImpl extends ServiceImpl<VehicleAuditMapper, VehicleAudit>
        implements VehicleAuditService {

    private final SysVehicleMapper vehicleMapper;

    public VehicleAuditServiceImpl(SysVehicleMapper vehicleMapper) {
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VehicleAuditVO submit(VehicleAuditSubmitCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long applicantId = TenantContext.getUserId();

        SysVehicle vehicle = vehicleMapper.selectById(cmd.getVehicleId());
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        // 检查是否已有待审核记录
        long pendingCount = baseMapper.countPendingByVehicleId(cmd.getVehicleId(), tenantId);
        if (pendingCount > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该车辆已有待审核的申请，请勿重复提交");
        }

        VehicleAudit entity = new VehicleAudit();
        entity.setVehicleId(cmd.getVehicleId());
        entity.setPlateNumber(vehicle.getPlateNumber());
        entity.setApplyType(cmd.getApplyType());
        entity.setApplyReason(cmd.getApplyReason());
        entity.setApplicantId(applicantId);
        entity.setApplicantName(cmd.getApplicantName());
        entity.setApplicantPhone(cmd.getApplicantPhone());
        entity.setAuditStatus(VehicleAudit.STATUS_PENDING);
        entity.setTenantId(tenantId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("提交车辆审核申请: auditId={}, vehicleId={}, plate={}, applyType={}",
                entity.getId(), cmd.getVehicleId(), vehicle.getPlateNumber(), cmd.getApplyType());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VehicleAuditVO process(Long id, VehicleAuditProcessCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long auditorId = TenantContext.getUserId();

        VehicleAudit entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "审核记录不存在");
        }

        // 只能处理待审核或待补充状态的记录
        if (!VehicleAudit.STATUS_PENDING.equals(entity.getAuditStatus())
                && !VehicleAudit.STATUS_NEED_INFO.equals(entity.getAuditStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该审核记录已处理，当前状态: " + entity.getAuditStatus());
        }

        entity.setAuditStatus(cmd.getAuditStatus());
        entity.setAuditResult(cmd.getAuditResult());
        entity.setAuditorId(auditorId);
        entity.setAuditorName(""); // TODO: 从用户服务获取审核人姓名
        entity.setAuditedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("处理车辆审核: auditId={}, status={}, vehicleId={}",
                id, cmd.getAuditStatus(), entity.getVehicleId());

        return toVO(entity);
    }

    @Override
    public VehicleAuditVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        VehicleAudit entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "审核记录不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<VehicleAuditVO> pageList(IPage<VehicleAudit> page, String auditStatus, String applyType, String plateNumber) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<VehicleAudit> wrapper = new LambdaQueryWrapper<VehicleAudit>()
                .eq(VehicleAudit::getTenantId, tenantId)
                .eq(auditStatus != null && !auditStatus.isEmpty(), VehicleAudit::getAuditStatus, auditStatus)
                .eq(applyType != null && !applyType.isEmpty(), VehicleAudit::getApplyType, applyType)
                .like(plateNumber != null && !plateNumber.isEmpty(), VehicleAudit::getPlateNumber, plateNumber.toUpperCase())
                .isNull(VehicleAudit::getDeletedAt)
                .orderByDesc(VehicleAudit::getCreatedAt);

        IPage<VehicleAudit> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    public List<VehicleAuditVO> listByVehicleId(Long vehicleId) {
        Long tenantId = TenantContext.getTenantId();
        List<VehicleAudit> list = baseMapper.selectByVehicleId(vehicleId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    private VehicleAuditVO toVO(VehicleAudit entity) {
        VehicleAuditVO vo = new VehicleAuditVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
