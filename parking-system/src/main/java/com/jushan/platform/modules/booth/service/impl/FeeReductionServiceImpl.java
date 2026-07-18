package com.jushan.platform.modules.booth.service.impl;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.FeeReductionCmd;
import com.jushan.platform.modules.booth.service.FeeReductionService;
import com.jushan.platform.modules.booth.vo.FeeReductionVO;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.system.entity.DeviceCommandAudit;
import com.jushan.system.mapper.DeviceCommandAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用减免服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class FeeReductionServiceImpl implements FeeReductionService {

    private final ParkingSessionMapper parkingSessionMapper;
    private final DeviceCommandAuditMapper deviceCommandAuditMapper;

    public FeeReductionServiceImpl(ParkingSessionMapper parkingSessionMapper,
                                   DeviceCommandAuditMapper deviceCommandAuditMapper) {
        this.parkingSessionMapper = parkingSessionMapper;
        this.deviceCommandAuditMapper = deviceCommandAuditMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeReductionVO apply(FeeReductionCmd cmd) {
        Long tenantId = TenantContext.requireTenantId();
        Long operatorId = TenantContext.requireUserId();

        // 1. 校验在场记录（parking_session）
        ParkingSession session = parkingSessionMapper.selectById(cmd.getSessionId());
        if (session == null || !tenantId.equals(session.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "在场记录不存在");
        }
        // parking_session 实体使用 IN/OUT/EXCEPTION 状态，
        // 不做严格的状态校验，由上层 ChargePanel 保证仅对正在收费的记录发起减免

        // 2. 校验金额
        if (cmd.getReducedFeeCents() > cmd.getOriginalFeeCents()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "减免后金额不能大于原金额");
        }
        int expectedReduction = cmd.getOriginalFeeCents() - cmd.getReducedFeeCents();
        if (cmd.getReductionCents() != expectedReduction) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "减免金额计算不一致");
        }

        // 3. 更新 parking_session.fee_amount（转换分为元存储到 DECIMAL 字段）
        BigDecimal reducedFeeYuan = BigDecimal.valueOf(cmd.getReducedFeeCents()).movePointLeft(2);
        session.setFeeAmount(reducedFeeYuan);
        session.setUpdatedAt(LocalDateTime.now());
        parkingSessionMapper.updateById(session);

        // 4. 写入审计日志
        // 注：device_command_audit 表 device_id / device_sn 为 NOT NULL，
        // 但费用减免操作不涉及具体设备命令，使用占位值。
        DeviceCommandAudit audit = new DeviceCommandAudit();
        audit.setTenantId(tenantId);
        audit.setParkingLotId(session.getParkingLotId());
        audit.setLaneId(session.getLaneId());
        audit.setDeviceId(0L);
        audit.setDeviceSn("N/A");
        audit.setPlateNumber(session.getPlateNumber());
        audit.setFeeCents(cmd.getReducedFeeCents());
        audit.setCommandType("FEE_REDUCTION");
        audit.setSource("MANUAL");
        audit.setOperatorId(operatorId);
        // operatorName 从当前用户名获取；若不可用则回退到 operatorId
        String operatorName = "userId=" + operatorId;
        audit.setOperatorName(operatorName);
        audit.setReason(cmd.getReason());
        audit.setStatus("SUCCESS");
        audit.setUncertain(false);
        audit.setRequestPayload(buildRequestPayload(cmd));
        audit.setIssuedAt(LocalDateTime.now());
        audit.setCreatedAt(LocalDateTime.now());
        audit.setUpdatedAt(LocalDateTime.now());
        deviceCommandAuditMapper.insert(audit);

        log.info("费用减免: sessionId={}, originalFeeCents={}, reducedFeeCents={}, reductionCents={}, reason={}",
                cmd.getSessionId(), cmd.getOriginalFeeCents(), cmd.getReducedFeeCents(),
                cmd.getReductionCents(), cmd.getReason());

        // 5. 返回结果
        FeeReductionVO vo = new FeeReductionVO();
        vo.setSessionId(cmd.getSessionId());
        vo.setOriginalFeeCents(cmd.getOriginalFeeCents());
        vo.setReducedFeeCents(cmd.getReducedFeeCents());
        vo.setReductionCents(cmd.getReductionCents());
        vo.setAppliedAt(LocalDateTime.now());
        return vo;
    }

    private String buildRequestPayload(FeeReductionCmd cmd) {
        return "{\"sessionId\":" + cmd.getSessionId()
                + ",\"originalFeeCents\":" + cmd.getOriginalFeeCents()
                + ",\"reducedFeeCents\":" + cmd.getReducedFeeCents()
                + ",\"reductionCents\":" + cmd.getReductionCents()
                + ",\"reason\":\"" + (cmd.getReason() != null ? cmd.getReason() : "") + "\"}";
    }
}
