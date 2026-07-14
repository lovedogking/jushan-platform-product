package com.jushan.platform.modules.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.dto.VehicleCreateCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleMultiPlateBindCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleUpdateCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.entity.SysVehicleMultiPlate;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMultiPlateMapper;
import com.jushan.platform.modules.vehicle.service.SysVehicleService;
import com.jushan.platform.modules.vehicle.vo.VehicleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 车辆主表服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class SysVehicleServiceImpl extends ServiceImpl<SysVehicleMapper, SysVehicle> implements SysVehicleService {

    private final SysVehicleMultiPlateMapper multiPlateMapper;

    public SysVehicleServiceImpl(SysVehicleMultiPlateMapper multiPlateMapper) {
        this.multiPlateMapper = multiPlateMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VehicleVO create(VehicleCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        // 车牌号标准化大写
        String standardizedPlate = cmd.getPlateNumber().toUpperCase();

        // 校验车牌号是否已存在
        SysVehicle existing = baseMapper.selectByPlateNumber(standardizedPlate, tenantId);
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "车牌号已存在");
        }

        SysVehicle entity = new SysVehicle();
        BeanUtils.copyProperties(cmd, entity);
        entity.setPlateNumber(standardizedPlate);
        entity.setTenantId(tenantId);
        entity.setStatus(SysVehicle.STATUS_ACTIVE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("新增车辆成功: vehicleId={}, plate={}, tenantId={}", entity.getId(), standardizedPlate, tenantId);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VehicleVO updateVehicle(Long id, VehicleUpdateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        SysVehicle entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        BeanUtils.copyProperties(cmd, entity);
        if (cmd.getPlateNumber() != null) {
            entity.setPlateNumber(cmd.getPlateNumber().toUpperCase());
        }
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("编辑车辆成功: vehicleId={}", id);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVehicle(Long id) {
        Long tenantId = TenantContext.getTenantId();

        SysVehicle entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        baseMapper.deleteById(id);
        log.info("删除车辆成功: vehicleId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        Long tenantId = TenantContext.getTenantId();

        for (Long id : ids) {
            SysVehicle entity = baseMapper.selectById(id);
            if (entity != null && tenantId.equals(entity.getTenantId())) {
                baseMapper.deleteById(id);
            }
        }

        log.info("批量删除车辆成功: count={}, ids={}", ids.size(), ids);
    }

    @Override
    public VehicleVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        SysVehicle entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<VehicleVO> pageList(IPage<SysVehicle> page, String plateNumber, String vehicleType,
                                      Long departmentId, Long parkingLotId, String status) {
        Long tenantId = TenantContext.getTenantId();
        log.info("pageList 租户ID: tenantId={}", tenantId);

        LambdaQueryWrapper<SysVehicle> wrapper = new LambdaQueryWrapper<SysVehicle>()
                .eq(SysVehicle::getTenantId, tenantId);

        if (plateNumber != null && !plateNumber.isEmpty()) {
            wrapper.like(SysVehicle::getPlateNumber, plateNumber.toUpperCase());
        }
        if (vehicleType != null && !vehicleType.isEmpty()) {
            wrapper.eq(SysVehicle::getVehicleType, vehicleType);
        }
        if (departmentId != null) {
            wrapper.eq(SysVehicle::getDepartmentId, departmentId);
        }
        if (parkingLotId != null) {
            wrapper.eq(SysVehicle::getParkingLotId, parkingLotId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SysVehicle::getStatus, status);
        }

        wrapper.orderByDesc(SysVehicle::getCreatedAt);

        IPage<SysVehicle> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMultiPlate(Long vehicleId, VehicleMultiPlateBindCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        SysVehicle vehicle = baseMapper.selectById(vehicleId);
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        // TODO: 校验绑定上限（TASK-0402 后续优化）
        long bindCount = multiPlateMapper.countByVehicleId(vehicleId);
        if (bindCount >= 10) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "绑定车牌数量已达上限（10个）");
        }

        String standardizedPlate = cmd.getPlateNumber().toUpperCase();

        SysVehicleMultiPlate bind = new SysVehicleMultiPlate();
        bind.setVehicleId(vehicleId);
        bind.setPlateNumber(standardizedPlate);
        bind.setStatus(SysVehicleMultiPlate.STATUS_ACTIVE);
        bind.setTenantId(tenantId);
        bind.setCreatedAt(LocalDateTime.now());
        bind.setUpdatedAt(LocalDateTime.now());

        multiPlateMapper.insert(bind);
        log.info("添加一位多车绑定成功: vehicleId={}, plate={}", vehicleId, standardizedPlate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMultiPlate(Long vehicleId, Long bindId) {
        Long tenantId = TenantContext.getTenantId();

        SysVehicle vehicle = baseMapper.selectById(vehicleId);
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        multiPlateMapper.deleteById(bindId);
        log.info("解除一位多车绑定成功: vehicleId={}, bindId={}", vehicleId, bindId);
    }

    @Override
    public VehicleVO findByPlateNumber(String plateNumber) {
        Long tenantId = TenantContext.getTenantId();
        SysVehicle entity = baseMapper.selectByPlateNumber(plateNumber.toUpperCase(), tenantId);
        return entity != null ? toVO(entity) : null;
    }

    private VehicleVO toVO(SysVehicle entity) {
        VehicleVO vo = new VehicleVO();
        BeanUtils.copyProperties(entity, vo);

        // 加载一位多车绑定
        List<SysVehicleMultiPlate> binds = multiPlateMapper.selectByVehicleId(entity.getId());
        vo.setMultiPlates(binds.stream().map(SysVehicleMultiPlate::getPlateNumber).collect(Collectors.toList()));

        return vo;
    }
}
