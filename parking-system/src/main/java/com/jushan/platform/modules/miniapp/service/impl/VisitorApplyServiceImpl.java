package com.jushan.platform.modules.miniapp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.miniapp.dto.VisitorApplyAuditCmd;
import com.jushan.platform.modules.miniapp.dto.VisitorApplyCreateCmd;
import com.jushan.platform.modules.miniapp.entity.VisitorApply;
import com.jushan.platform.modules.miniapp.mapper.VisitorApplyMapper;
import com.jushan.platform.modules.miniapp.service.VisitorApplyService;
import com.jushan.platform.modules.miniapp.vo.VisitorApplyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 访客预约申请服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class VisitorApplyServiceImpl extends ServiceImpl<VisitorApplyMapper, VisitorApply>
        implements VisitorApplyService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VisitorApplyVO submit(VisitorApplyCreateCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long applicantId = TenantContext.getUserId();

        VisitorApply entity = new VisitorApply();
        BeanUtils.copyProperties(cmd, entity);
        entity.setPlateNumber(cmd.getPlateNumber().toUpperCase());
        entity.setApplyStatus(VisitorApply.STATUS_PENDING);
        entity.setApplicantId(applicantId);
        entity.setTenantId(tenantId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("访客预约申请提交: applyId={}, plate={}, visitorName={}",
                entity.getId(), entity.getPlateNumber(), cmd.getVisitorName());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VisitorApplyVO audit(Long id, VisitorApplyAuditCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long auditorId = TenantContext.getUserId();

        VisitorApply entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "预约申请不存在");
        }

        if (!VisitorApply.STATUS_PENDING.equals(entity.getApplyStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该预约申请已处理，当前状态: " + entity.getApplyStatus());
        }

        entity.setApplyStatus(cmd.getApplyStatus());
        entity.setAuditResult(cmd.getAuditResult());
        entity.setAuditorId(auditorId);
        entity.setAuditedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("访客预约审核: applyId={}, status={}", id, cmd.getApplyStatus());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VisitorApplyVO cancel(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Long applicantId = TenantContext.getUserId();

        VisitorApply entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "预约申请不存在");
        }

        // 只能取消自己的申请
        if (!applicantId.equals(entity.getApplicantId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能取消自己的预约申请");
        }

        if (!VisitorApply.STATUS_PENDING.equals(entity.getApplyStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该预约申请已处理，无法取消");
        }

        entity.setApplyStatus(VisitorApply.STATUS_CANCELLED);
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("访客预约取消: applyId={}", id);

        return toVO(entity);
    }

    @Override
    public VisitorApplyVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        VisitorApply entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "预约申请不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<VisitorApplyVO> pageList(IPage<VisitorApply> page, Long parkingLotId, String applyStatus, String plateNumber) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<VisitorApply> wrapper = new LambdaQueryWrapper<VisitorApply>()
                .eq(VisitorApply::getTenantId, tenantId)
                .eq(parkingLotId != null, VisitorApply::getParkingLotId, parkingLotId)
                .eq(applyStatus != null && !applyStatus.isEmpty(), VisitorApply::getApplyStatus, applyStatus)
                .like(plateNumber != null && !plateNumber.isEmpty(), VisitorApply::getPlateNumber, plateNumber.toUpperCase())
                .isNull(VisitorApply::getDeletedAt)
                .orderByDesc(VisitorApply::getCreatedAt);

        IPage<VisitorApply> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    public List<VisitorApplyVO> listByParkingLotId(Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        List<VisitorApply> list = baseMapper.selectByParkingLotId(parkingLotId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<VisitorApplyVO> listByApplicantId(Long applicantId) {
        Long tenantId = TenantContext.getTenantId();
        List<VisitorApply> list = baseMapper.selectByApplicantId(applicantId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    private VisitorApplyVO toVO(VisitorApply entity) {
        VisitorApplyVO vo = new VisitorApplyVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
