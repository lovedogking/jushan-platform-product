package com.jushan.platform.modules.booth.service.impl;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.service.RecognitionEventService;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
import com.jushan.system.service.BillingEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 识别事件处理服务实现。
 * <p>
 * 【预留】开闸接口返回 mock 结果，实际开闸待 Device Access v1.0 实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class RecognitionEventServiceImpl implements RecognitionEventService {

    private final VehicleTypeDecisionService vehicleTypeDecisionService;
    private final ParkingSessionService parkingSessionService;
    private final BillingEngine billingEngine;

    public RecognitionEventServiceImpl(VehicleTypeDecisionService vehicleTypeDecisionService,
                                       ParkingSessionService parkingSessionService,
                                       BillingEngine billingEngine) {
        this.vehicleTypeDecisionService = vehicleTypeDecisionService;
        this.parkingSessionService = parkingSessionService;
        this.billingEngine = billingEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecognitionResultVO handleEvent(RecognitionEventCmd cmd) {
        String plateNumber = cmd.getPlateNumber().toUpperCase();
        Long tenantId = TenantContext.getTenantId();

        log.info("处理识别事件: plate={}, direction={}, laneId={}, lotId={}",
                plateNumber, cmd.getDirection(), cmd.getLaneId(), cmd.getParkingLotId());

        RecognitionResultVO result = new RecognitionResultVO();
        result.setPlateNumber(plateNumber);

        // 1. 车辆类型判定
        VehicleTypeDecisionVO decision = vehicleTypeDecisionService.decide(plateNumber);
        result.setVehicleType(decision.getVehicleType());

        // 2. 方向处理
        if ("ENTRY".equals(cmd.getDirection())) {
            return handleEntry(cmd, decision, result, tenantId);
        } else if ("EXIT".equals(cmd.getDirection())) {
            return handleExit(cmd, decision, result, tenantId);
        } else {
            result.setAllowPass(false);
            result.setException(true);
            result.setExceptionType("UNKNOWN_DIRECTION");
            result.setResultMessage("未知方向: " + cmd.getDirection());
            return result;
        }
    }

    @Override
    public RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason) {
        log.info("人工开闸请求: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);
        // 【预留】开闸为 mock，Device Access v0.2 无 gate/open 接口
        result.setGateOpened(false);
        result.setGateResult("NOT_IMPLEMENTED_IN_V0.2: 人工开闸请求已记录，实际闸杆动作待 Device Access v1.0 实现");
        result.setResultMessage("人工开闸: " + reason);
        result.setException(false);

        // 记录审计日志（TODO: 接入审计服务）
        log.info("人工开闸审计: laneId={}, operatorId={}, reason={}, result=UNCERTAIN", laneId, operatorId, reason);

        return result;
    }

    private RecognitionResultVO handleEntry(RecognitionEventCmd cmd, VehicleTypeDecisionVO decision,
                                           RecognitionResultVO result, Long tenantId) {
        // 检查是否允许入场
        if (!Boolean.TRUE.equals(decision.getAllowEntry())) {
            result.setAllowPass(false);
            result.setGateOpened(false);
            result.setResultMessage(decision.getDecisionReason());
            result.setException(true);
            result.setExceptionType("ENTRY_DENIED");
            log.warn("入场拒绝: plate={}, reason={}", cmd.getPlateNumber(), decision.getDecisionReason());
            return result;
        }

        // 创建入场记录
        ParkingSessionEntryCmd entryCmd = new ParkingSessionEntryCmd();
        entryCmd.setParkingLotId(cmd.getParkingLotId());
        entryCmd.setLaneId(cmd.getLaneId());
        entryCmd.setPlateNumber(cmd.getPlateNumber());
        entryCmd.setPlateColor(cmd.getPlateColor());
        entryCmd.setVehicleType(decision.getVehicleType());
        entryCmd.setEntryImage(cmd.getCaptureImage());

        var sessionVO = parkingSessionService.entry(entryCmd);
        result.setSessionId(sessionVO.getId());

        result.setAllowPass(true);
        result.setFeeAmount(BigDecimal.ZERO);

        // 【预留】开闸为 mock
        result.setGateOpened(false);
        result.setGateResult("NOT_IMPLEMENTED_IN_V0.2: 入场开闸请求已记录，实际闸杆动作待 Device Access v1.0 实现");
        result.setResultMessage(decision.getDecisionReason() + "，在场记录已创建");
        result.setException(false);

        log.info("入场处理完成: plate={}, sessionId={}, type={}",
                cmd.getPlateNumber(), sessionVO.getId(), decision.getVehicleType());

        return result;
    }

    private RecognitionResultVO handleExit(RecognitionEventCmd cmd, VehicleTypeDecisionVO decision,
                                           RecognitionResultVO result, Long tenantId) {
        // 查询在场记录
        var sessionVO = parkingSessionService.getInByPlateNumber(cmd.getPlateNumber());
        if (sessionVO == null) {
            result.setAllowPass(false);
            result.setException(true);
            result.setExceptionType("NO_ENTRY_RECORD");
            result.setResultMessage("未找到入场记录，无法出场");
            log.warn("出场异常: plate={}, 无入场记录", cmd.getPlateNumber());
            return result;
        }

        // 计算费用：接入 BillingEngine，替换硬编码 5 元
        BigDecimal feeAmount = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(decision.getNeedCharge())) {
            try {
                int feeCents = billingEngine.calculateFee(
                        cmd.getParkingLotId(),
                        sessionVO.getEntryTime(),
                        LocalDateTime.now()
                );
                feeAmount = BigDecimal.valueOf(feeCents).movePointLeft(2);
                log.info("计费引擎计算费用: plate={}, feeCents={}, feeAmount={}",
                        cmd.getPlateNumber(), feeCents, feeAmount);
            } catch (Exception e) {
                log.warn("计费引擎计算失败，按零元处理: plate={}, error={}",
                        cmd.getPlateNumber(), e.getMessage());
                feeAmount = BigDecimal.ZERO;
            }
        }

        // 更新出场记录
        ParkingSessionExitCmd exitCmd = new ParkingSessionExitCmd();
        exitCmd.setSessionId(sessionVO.getId());
        exitCmd.setExitLaneId(cmd.getLaneId());
        exitCmd.setExitImage(cmd.getCaptureImage());
        exitCmd.setFeeAmount(feeAmount);

        var updatedSession = parkingSessionService.exit(exitCmd);
        result.setSessionId(updatedSession.getId());

        result.setAllowPass(true);
        result.setFeeAmount(feeAmount);

        // 【预留】开闸为 mock
        result.setGateOpened(false);
        result.setGateResult("NOT_IMPLEMENTED_IN_V0.2: 出场开闸请求已记录，实际闸杆动作待 Device Access v1.0 实现");
        result.setResultMessage(decision.getDecisionReason() + "，费用: " + feeAmount + " 元");
        result.setException(false);

        log.info("出场处理完成: plate={}, sessionId={}, fee={}",
                cmd.getPlateNumber(), updatedSession.getId(), feeAmount);

        return result;
    }
}
