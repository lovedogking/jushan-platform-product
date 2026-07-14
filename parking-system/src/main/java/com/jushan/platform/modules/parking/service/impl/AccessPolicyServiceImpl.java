package com.jushan.platform.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.dto.AccessPolicyCreateCmd;
import com.jushan.platform.modules.parking.entity.AccessPolicy;
import com.jushan.platform.modules.parking.mapper.AccessPolicyMapper;
import com.jushan.platform.modules.parking.service.AccessPolicyService;
import com.jushan.platform.modules.parking.vo.AccessPolicyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 车辆进出策略配置服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class AccessPolicyServiceImpl extends ServiceImpl<AccessPolicyMapper, AccessPolicy>
        implements AccessPolicyService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccessPolicyVO create(AccessPolicyCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        // 校验是否已存在相同策略键
        LambdaQueryWrapper<AccessPolicy> checkWrapper = new LambdaQueryWrapper<AccessPolicy>()
                .eq(AccessPolicy::getTenantId, tenantId)
                .eq(AccessPolicy::getParkingLotId, cmd.getParkingLotId())
                .eq(AccessPolicy::getPolicyType, cmd.getPolicyType())
                .eq(AccessPolicy::getPolicyKey, cmd.getPolicyKey())
                .isNull(AccessPolicy::getDeletedAt);

        long count = baseMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该停车场已存在相同的策略键配置");
        }

        AccessPolicy entity = new AccessPolicy();
        BeanUtils.copyProperties(cmd, entity);
        entity.setTenantId(tenantId);
        entity.setStatus(AccessPolicy.STATUS_ACTIVE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("创建进出策略: policyId={}, lotId={}, type={}, key={}",
                entity.getId(), cmd.getParkingLotId(), cmd.getPolicyType(), cmd.getPolicyKey());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccessPolicyVO update(Long id, AccessPolicyCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();

        AccessPolicy entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "策略配置不存在");
        }

        BeanUtils.copyProperties(cmd, entity);
        entity.setId(id);
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("更新进出策略: policyId={}", id);

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();

        AccessPolicy entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "策略配置不存在");
        }

        baseMapper.deleteById(id);
        log.info("删除进出策略: policyId={}", id);
    }

    @Override
    public AccessPolicyVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        AccessPolicy entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "策略配置不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<AccessPolicyVO> pageList(IPage<AccessPolicy> page, Long parkingLotId, String policyType) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<AccessPolicy> wrapper = new LambdaQueryWrapper<AccessPolicy>()
                .eq(AccessPolicy::getTenantId, tenantId)
                .eq(parkingLotId != null, AccessPolicy::getParkingLotId, parkingLotId)
                .eq(policyType != null && !policyType.isEmpty(), AccessPolicy::getPolicyType, policyType)
                .isNull(AccessPolicy::getDeletedAt)
                .orderByAsc(AccessPolicy::getSortOrder)
                .orderByDesc(AccessPolicy::getCreatedAt);

        IPage<AccessPolicy> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    public List<AccessPolicyVO> listByParkingLotId(Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        List<AccessPolicy> list = baseMapper.selectByParkingLotId(parkingLotId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<AccessPolicyVO> listByType(Long parkingLotId, String policyType) {
        Long tenantId = TenantContext.getTenantId();
        List<AccessPolicy> list = baseMapper.selectByType(parkingLotId, policyType, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Map<String, String> getPolicyMap(Long parkingLotId, String policyType) {
        List<AccessPolicyVO> policies = listByType(parkingLotId, policyType);
        return policies.stream()
                .collect(Collectors.toMap(AccessPolicyVO::getPolicyKey, AccessPolicyVO::getPolicyValue, (v1, v2) -> v1));
    }

    private AccessPolicyVO toVO(AccessPolicy entity) {
        AccessPolicyVO vo = new AccessPolicyVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
