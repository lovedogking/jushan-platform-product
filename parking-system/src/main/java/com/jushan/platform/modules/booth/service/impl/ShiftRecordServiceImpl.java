package com.jushan.platform.modules.booth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.ShiftCloseCmd;
import com.jushan.platform.modules.booth.dto.ShiftStartCmd;
import com.jushan.platform.modules.booth.entity.ShiftRecord;
import com.jushan.platform.modules.booth.mapper.ShiftRecordMapper;
import com.jushan.platform.modules.booth.service.ShiftRecordService;
import com.jushan.platform.modules.booth.vo.ShiftRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 交接班管理服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class ShiftRecordServiceImpl extends ServiceImpl<ShiftRecordMapper, ShiftRecord>
        implements ShiftRecordService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftRecordVO startShift(ShiftStartCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();

        // 检查是否已有未交班记录
        ShiftRecord existing = baseMapper.selectOpenByOperator(operatorId, tenantId);
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "您已有未交班的记录，请先交班后再开班");
        }

        ShiftRecord entity = new ShiftRecord();
        entity.setParkingLotId(cmd.getParkingLotId());
        entity.setOperatorId(operatorId);
        entity.setOperatorName(cmd.getOperatorName());
        entity.setShiftType(cmd.getShiftType());
        entity.setStartTime(LocalDateTime.now());
        entity.setEntryCount(0);
        entity.setExitCount(0);
        entity.setFeeAmount(java.math.BigDecimal.ZERO);
        entity.setCashAmount(java.math.BigDecimal.ZERO);
        entity.setOnlineAmount(java.math.BigDecimal.ZERO);
        entity.setExceptionCount(0);
        entity.setHandoverStatus(ShiftRecord.STATUS_OPEN);
        entity.setTenantId(tenantId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.insert(entity);
        log.info("交接班开班: shiftId={}, operatorId={}, lotId={}",
                entity.getId(), operatorId, cmd.getParkingLotId());

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftRecordVO closeShift(ShiftCloseCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();

        ShiftRecord entity = baseMapper.selectById(cmd.getShiftId());
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "交接班记录不存在");
        }

        if (!ShiftRecord.STATUS_OPEN.equals(entity.getHandoverStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该记录已交班");
        }

        // 校验只能交自己的班
        if (!operatorId.equals(entity.getOperatorId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能交自己的班");
        }

        entity.setEndTime(LocalDateTime.now());
        entity.setHandoverStatus(ShiftRecord.STATUS_CLOSED);
        entity.setHandoverTo(cmd.getHandoverTo());
        entity.setHandoverRemark(cmd.getHandoverRemark());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("交接班交班: shiftId={}, operatorId={}", entity.getId(), operatorId);

        return toVO(entity);
    }

    @Override
    public ShiftRecordVO getCurrentShift() {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();

        ShiftRecord entity = baseMapper.selectOpenByOperator(operatorId, tenantId);
        return entity != null ? toVO(entity) : null;
    }

    @Override
    public ShiftRecordVO detail(Long id) {
        Long tenantId = TenantContext.getTenantId();

        ShiftRecord entity = baseMapper.selectById(id);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "交接班记录不存在");
        }

        return toVO(entity);
    }

    @Override
    public IPage<ShiftRecordVO> pageList(IPage<ShiftRecord> page, Long parkingLotId, String status) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<ShiftRecord> wrapper = new LambdaQueryWrapper<ShiftRecord>()
                .eq(ShiftRecord::getTenantId, tenantId)
                .eq(parkingLotId != null, ShiftRecord::getParkingLotId, parkingLotId)
                .eq(status != null && !status.isEmpty(), ShiftRecord::getHandoverStatus, status)
                .isNull(ShiftRecord::getDeletedAt)
                .orderByDesc(ShiftRecord::getStartTime);

        IPage<ShiftRecord> entityPage = baseMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toVO);
    }

    @Override
    public List<ShiftRecordVO> listByParkingLotId(Long parkingLotId) {
        Long tenantId = TenantContext.getTenantId();
        List<ShiftRecord> list = baseMapper.selectByParkingLotId(parkingLotId, tenantId);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    private ShiftRecordVO toVO(ShiftRecord entity) {
        ShiftRecordVO vo = new ShiftRecordVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
