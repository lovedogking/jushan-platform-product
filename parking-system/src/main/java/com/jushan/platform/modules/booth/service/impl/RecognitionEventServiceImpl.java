package com.jushan.platform.modules.booth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.service.RecognitionEventService;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.dto.CommandResultDTO;
import com.jushan.system.entity.Device;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.service.BillingEngine;
import com.jushan.system.service.MonitorAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 识别事件处理服务实现。
 * <p>
 * 处理入场/出场识别事件，调用 Device Access v0.4 真实开闸。
 * 开闸失败不阻塞业务记录（入场记录已创建、出场费用已计算）。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>deviceSn 从平台设备台账可信记录读取，不信任前端输入</li>
 *   <li>开闸禁止自动重试，失败后由岗亭人工兜底</li>
 *   <li>开闸异常标记为 UNCERTAIN，保留完整审计信息</li>
 * </ul>
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
    private final DeviceMapper deviceMapper;
    private final DeviceAccessClient deviceAccessClient;
    private final MonitorAlertService monitorAlertService;

    public RecognitionEventServiceImpl(VehicleTypeDecisionService vehicleTypeDecisionService,
                                       ParkingSessionService parkingSessionService,
                                       BillingEngine billingEngine,
                                       DeviceMapper deviceMapper,
                                       DeviceAccessClient deviceAccessClient,
                                       MonitorAlertService monitorAlertService) {
        this.vehicleTypeDecisionService = vehicleTypeDecisionService;
        this.parkingSessionService = parkingSessionService;
        this.billingEngine = billingEngine;
        this.deviceMapper = deviceMapper;
        this.deviceAccessClient = deviceAccessClient;
        this.monitorAlertService = monitorAlertService;
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

        // 查找车道绑定的 GATE 设备
        Device gateDevice = findGateDevice(laneId);
        if (gateDevice == null) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("未找到车道对应的 GATE 设备，无法开闸");
            result.setResultMessage("人工开闸失败: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_DEVICE_NOT_FOUND");
            log.warn("人工开闸失败: 车道无 GATE 设备, laneId={}, operatorId={}", laneId, operatorId);
            return result;
        }

        String deviceSn = gateDevice.getDeviceSn();
        // 调用 Device Access v0.4 真实开闸（不自动重试）
        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceAccessClient.openGate(deviceSn);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(null); // 一期无法确认闸杆实际状态
            if (success) {
                result.setGateResult("人工开闸成功");
                result.setResultMessage("人工开闸: " + reason);
                result.setException(false);
                log.info("人工开闸成功: laneId={}, operatorId={}, deviceSn={}, reason={}",
                        laneId, operatorId, deviceSn, reason);
            } else {
                result.setGateResult("人工开闸失败: " + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                result.setResultMessage("人工开闸失败: " + reason);
                result.setException(true);
                result.setExceptionType("GATE_OPEN_FAILED");
                log.warn("人工开闸设备返回失败: laneId={}, operatorId={}, deviceSn={}, deviceCode={}, message={}",
                        laneId, operatorId, deviceSn, gateResult.getDeviceCode(), gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("人工开闸异常（UNCERTAIN）: " + e.getMessage());
            result.setResultMessage("人工开闸异常: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_OPEN_UNCERTAIN");
            log.error("人工开闸异常（UNCERTAIN）: laneId={}, operatorId={}, deviceSn={}, error={}",
                    laneId, operatorId, deviceSn, e.getMessage());
        }

        // 记录审计日志
        log.info("人工开闸审计: laneId={}, operatorId={}, reason={}, deviceSn={}, gateCommandSent={}, gateDeviceAck={}, gateOpened={}",
                laneId, operatorId, reason, deviceSn,
                result.getGateCommandSent(), result.getGateDeviceAck(), result.getGateOpened());

        return result;
    }

    private RecognitionResultVO handleEntry(RecognitionEventCmd cmd, VehicleTypeDecisionVO decision,
                                           RecognitionResultVO result, Long tenantId) {
        // 检查是否允许入场
        if (!Boolean.TRUE.equals(decision.getAllowEntry())) {
            result.setAllowPass(false);
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
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

        // 调用 Device Access v0.4 真实开闸
        // 开闸失败不得回滚入场记录（异常被捕获，不向外传播）
        executeGateOpen(cmd.getParkingLotId(), cmd.getLaneId(), result, "ENTRY", cmd.getPlateNumber());

        result.setResultMessage(decision.getDecisionReason() + "，在场记录已创建");
        result.setException(false);

        log.info("入场处理完成: plate={}, sessionId={}, type={}, gateOpened={}",
                cmd.getPlateNumber(), sessionVO.getId(), decision.getVehicleType(), result.getGateOpened());

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

        // 调用 Device Access v0.4 真实开闸
        // 开闸失败不得回滚出场记录（异常被捕获，不向外传播）
        // 开闸失败时 ParkingSession 状态保持已出场（由 parkingSessionService.exit 决定），
        // 岗亭端可人工放行兜底
        executeGateOpen(cmd.getParkingLotId(), cmd.getLaneId(), result, "EXIT", cmd.getPlateNumber());

        result.setResultMessage(decision.getDecisionReason() + "，费用: " + feeAmount + " 元");
        result.setException(false);

        log.info("出场处理完成: plate={}, sessionId={}, fee={}, gateOpened={}",
                cmd.getPlateNumber(), updatedSession.getId(), feeAmount, result.getGateOpened());

        return result;
    }

    // ==================== 开闸辅助方法 ====================

    /**
     * 执行开闸操作，填充三层状态到 result。
     * <p>
     * 从可信车道绑定关系中查找 GATE 设备，调用 Device Access v0.4 开闸接口。
     * 所有异常均被捕获，不向外传播（保证入场/出场记录不因开闸失败而回滚）。
     *
     * @param parkingLotId 停车场 ID（用于告警）
     * @param laneId       车道 ID
     * @param result       结果 VO（原地修改）
     * @param direction    方向（ENTRY/EXIT，用于日志）
     * @param plateNumber  车牌号（用于日志）
     */
    private void executeGateOpen(Long parkingLotId, Long laneId, RecognitionResultVO result,
                                  String direction, String plateNumber) {
        Device gateDevice = findGateDevice(laneId);
        if (gateDevice == null) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("未找到车道对应的 GATE 设备");
            log.warn("{}开闸跳过: 车道无 GATE 设备, laneId={}, plate={}", direction, laneId, plateNumber);
            Long tenantId = TenantContext.getTenantId();
            monitorAlertService.createGateAlert(tenantId, parkingLotId, laneId, null,
                    "DEVICE_CONFIG_MISSING",
                    "开闸跳过: 车道无 GATE 设备, laneId=" + laneId + ", plate=" + plateNumber);
            return;
        }

        String deviceSn = gateDevice.getDeviceSn();
        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceAccessClient.openGate(deviceSn);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(null); // 一期无法确认闸杆实际状态
            if (success) {
                result.setGateResult(direction + "开闸成功");
            } else {
                result.setGateResult(direction + "开闸失败: "
                        + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                log.warn("{}开闸设备返回失败: plate={}, deviceSn={}, deviceCode={}, message={}",
                        direction, plateNumber, deviceSn, gateResult.getDeviceCode(), gateResult.getMessage());
                Long tenantId = TenantContext.getTenantId();
                monitorAlertService.createGateAlert(tenantId, parkingLotId, laneId, deviceSn,
                        "DEVICE_FAULT",
                        direction + "开闸设备返回失败: " + gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult(direction + "开闸异常（UNCERTAIN）: " + e.getMessage());
            log.error("{}开闸异常（UNCERTAIN）: plate={}, deviceSn={}, error={}",
                    direction, plateNumber, deviceSn, e.getMessage());
            Long tenantId = TenantContext.getTenantId();
            monitorAlertService.createGateAlert(tenantId, parkingLotId, laneId, deviceSn,
                    "DEVICE_UNCERTAIN",
                    direction + "开闸异常（UNCERTAIN）: " + e.getMessage());
        }
    }

    /**
     * 查找车道绑定的已启用 GATE 设备。
     * <p>
     * 通过 MyBatis-Plus 租户拦截器自动过滤租户数据，
     * deviceSn 从平台设备台账可信记录读取。
     *
     * @param laneId 车道 ID
     * @return GATE 设备实体，未找到返回 null
     */
    private Device findGateDevice(Long laneId) {
        if (laneId == null) {
            return null;
        }
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getLaneId, laneId)
                        .eq(Device::getDeviceType, "GATE")
                        .eq(Device::getStatus, "ENABLED")
                        .last("LIMIT 1"));
        return devices.isEmpty() ? null : devices.get(0);
    }
}
