package com.jushan.platform.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.dto.ParkingSpacePolicyCreateCmd;
import com.jushan.platform.modules.parking.entity.ParkingSpacePolicy;
import com.jushan.platform.modules.parking.mapper.ParkingSpacePolicyMapper;
import com.jushan.platform.modules.parking.service.ParkingSpacePolicyService;
import com.jushan.platform.modules.parking.vo.ParkingSpacePolicyVO;
import com.jushan.platform.modules.parking.vo.ParkingSpaceRemainVO;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 车位管控策略服务实现。
 * <p>
 * 支持余位计算与满位动作配置。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class ParkingSpacePolicyServiceImpl extends ServiceImpl<ParkingSpacePolicyMapper, ParkingSpacePolicy>
        implements ParkingSpacePolicyService {

    private final ParkingRecordMapper parkingRecordMapper;

    public ParkingSpacePolicyServiceImpl(ParkingRecordMapper parkingRecordMapper) {
        this.parkingRecordMapper = parkingRecordMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingSpacePolicyVO create(ParkingSpacePolicyCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        // 校验是否已存在相同停车场+区域的策略
        LambdaQueryWrapper<ParkingSpacePolicy> checkWrapper = new LambdaQueryWrapper<ParkingSpacePolicy>()
                .eq(ParkingSpacePolicy::getTenantId, tenantId)
                .eq(ParkingSpacePolicy::getParkingLotId, cmd.getParkingLotId())
                .eq(cmd.getZoneId() != null, ParkingSpacePolicy::getZoneId, cmd.getZoneId())
                .isNull(cmd.getZoneId() == null, ParkingSpacePolicy::getZoneId)
                .isNull(ParkingSpacePolicy::getDeletedAt);

        long count = baseMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该停车场/区域已存在车位管控策略");
        }

        ParkingSpacePolicy entity = new ParkingSpacePolicy();
        BeanUtils.copyProperties(cmd, entity);
        entity.setTenantId(tenantId);
        entity.setStatus(ParkingSpacePolicy.STATUS_ACTIVE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("创建车位管控策略: policyId={}, lotId={}, zoneId={}",
                entity.getId(), cmd.getParkingLotId(), cmd.getZoneId());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingSpacePolicyVO update(Long id, ParkingSpacePolicyCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        ParkingSpacePolicy entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车位管控策略不存在");
        }

        BeanUtils.copyProperties(cmd, entity);
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("更新车位管控策略: policyId={}", id);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();

        ParkingSpacePolicy entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车位管控策略不存在");
        }

        baseMapper.deleteById(id);
        log.info("删除车位管控策略: policyId={}", id);
    }

    @Override
    public ParkingSpacePolicyVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        ParkingSpacePolicy entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车位管控策略不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<ParkingSpacePolicyVO> pageList(IPage<ParkingSpacePolicy> page, Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<ParkingSpacePolicy> wrapper = new LambdaQueryWrapper<ParkingSpacePolicy>()
                .eq(ParkingSpacePolicy::getTenantId, tenantId)
                .eq(parkingLotId != null, ParkingSpacePolicy::getParkingLotId, parkingLotId)
                .isNull(ParkingSpacePolicy::getDeletedAt)
                .orderByDesc(ParkingSpacePolicy::getCreatedAt);

        IPage<ParkingSpacePolicy> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    public List<ParkingSpacePolicyVO> listByParkingLotId(Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        List<ParkingSpacePolicy> list = baseMapper.selectByParkingLotId(parkingLotId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ParkingSpacePolicyVO getByZoneId(Long zoneId) {
        Long tenantId = TenantContext.getTenantId();
        ParkingSpacePolicy entity = baseMapper.selectByZoneId(zoneId, tenantId);
        return entity != null ? toVO(entity) : null;
    }

    @Override
    public ParkingSpaceRemainVO calculateRemain(Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();

        // 获取全场策略
        LambdaQueryWrapper<ParkingSpacePolicy> wrapper = new LambdaQueryWrapper<ParkingSpacePolicy>()
                .eq(ParkingSpacePolicy::getTenantId, tenantId)
                .eq(ParkingSpacePolicy::getParkingLotId, parkingLotId)
                .isNull(ParkingSpacePolicy::getZoneId)
                .isNull(ParkingSpacePolicy::getDeletedAt)
                .eq(ParkingSpacePolicy::getStatus, ParkingSpacePolicy.STATUS_ACTIVE);

        ParkingSpacePolicy policy = baseMapper.selectOne(wrapper);
        if (policy == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "该停车场未配置车位管控策略");
        }

        int usedSpaces = countParkingVehicles(parkingLotId, tenantId);

        int totalSpaces = policy.getTotalSpaces();
        int remainSpaces = totalSpaces - usedSpaces;
        boolean warning = remainSpaces <= policy.getWarningThreshold();
        boolean full = remainSpaces <= 0;

        ParkingSpaceRemainVO remainVO = new ParkingSpaceRemainVO();
        remainVO.setParkingLotId(parkingLotId);
        remainVO.setZoneId(null);
        remainVO.setTotalSpaces(totalSpaces);
        remainVO.setUsedSpaces(usedSpaces);
        remainVO.setRemainSpaces(remainSpaces);
        remainVO.setWarning(warning);
        remainVO.setFull(full);

        return remainVO;
    }

    /**
     * 基于 parking_record 表实时统计指定停车场的在场车辆数。
     * <p>
     * 后续接入车场参数体系（任务包 1-1）后，根据参数
     * "monthly_card_count_in_remain" 和 "fixed_space_count_in_remain"
     * 决定是否排除月卡/固定车位的在场记录。
     */
    private int countParkingVehicles(Long parkingLotId, Long tenantId) {
        Long count = parkingRecordMapper.selectCount(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getTenantId, tenantId)
                        .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING)
                        .isNull(ParkingRecord::getDeletedAt));
        return count != null ? count.intValue() : 0;
    }

    private ParkingSpacePolicyVO toVO(ParkingSpacePolicy entity) {
        ParkingSpacePolicyVO vo = new ParkingSpacePolicyVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
