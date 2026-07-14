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

        // TODO: 从 Redis/数据库获取实际在场车辆数量（TASK-0601 实现）
        // 当前使用模拟数据，后续接入 parking_session 表
        int usedSpaces = 0; // 占位，后续实现

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

    private ParkingSpacePolicyVO toVO(ParkingSpacePolicy entity) {
        ParkingSpacePolicyVO vo = new ParkingSpacePolicyVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
