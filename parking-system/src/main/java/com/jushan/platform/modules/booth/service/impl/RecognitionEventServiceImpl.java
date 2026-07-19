package com.jushan.platform.modules.booth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.service.RecognitionEventService;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.dto.ParkingSessionExitCmd;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.dto.CommandResultDTO;
import com.jushan.system.entity.Device;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.service.BillingEngine;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.service.DeviceService;
import com.jushan.system.service.MonitorAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 识别事件处理服务实现。
 * <p>
 * 处理入场/出场识别事件，统一通过 Device Access → Adapter 下发开闸命令。
 * Adapter 根据设备类型自动选择 gate_direct_open 或 gpio_out 协议。
 * 开闸失败不阻塞业务记录（入场记录已创建、出场费用已计算）。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>deviceSn 从平台设备台账可信记录读取，不信任前端输入</li>
 *   <li>携带 commandId 支持网络瞬断场景的自动重试（最多 3 次，1s/5s/30s）</li>
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
    private final DeviceService deviceService;
    private final ParkingLaneMapper parkingLaneMapper;

    public RecognitionEventServiceImpl(VehicleTypeDecisionService vehicleTypeDecisionService,
                                       ParkingSessionService parkingSessionService,
                                       BillingEngine billingEngine,
                                       DeviceMapper deviceMapper,
                                       DeviceAccessClient deviceAccessClient,
                                       MonitorAlertService monitorAlertService,
                                       DeviceService deviceService,
                                       ParkingLaneMapper parkingLaneMapper) {
        this.vehicleTypeDecisionService = vehicleTypeDecisionService;
        this.parkingSessionService = parkingSessionService;
        this.billingEngine = billingEngine;
        this.deviceMapper = deviceMapper;
        this.deviceAccessClient = deviceAccessClient;
        this.monitorAlertService = monitorAlertService;
        this.deviceService = deviceService;
        this.parkingLaneMapper = parkingLaneMapper;
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

        // 1. 车辆类型判定（传入 parkingLotId 以命中白名单检查）
        VehicleTypeDecisionVO decision = vehicleTypeDecisionService.decide(
                plateNumber, cmd.getParkingLotId(), tenantId);
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
    public RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason,
                                              boolean isCharge, Integer feeCents, String plateNumber) {
        log.info("人工开闸请求: laneId={}, operatorId={}, reason={}, isCharge={}, feeCents={}, plateNumber={}",
                laneId, operatorId, reason, isCharge, feeCents, plateNumber);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);

        // 1. 先查找 GATE 设备（忽略租户拦截器，岗亭端 super_admin 可能无租户上下文）
        Device gateDevice = deviceMapper.selectByLaneIdAndTypeIgnoreTenant(laneId, "GATE");

        // 2. 若无 GATE 设备，查找带 OPEN_GATE 能力的 CAMERA 设备（如臻识 C5H GPIO 控制）
        if (gateDevice == null) {
            Device camera = deviceMapper.selectByLaneIdAndTypeIgnoreTenant(laneId, "CAMERA");
            if (camera != null && camera.getCapabilities() != null
                    && camera.getCapabilities().contains("OPEN_GATE")) {
                gateDevice = camera;
            }
        }

        // 3. 若当前车道无匹配设备，回退到同一停车场的 CAMERA+OPEN_GATE 设备
        if (gateDevice == null) {
            ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
            if (lane != null && lane.getLotId() != null) {
                gateDevice = deviceMapper.selectCameraWithOpenGateByLotIdIgnoreTenant(lane.getLotId());
                if (gateDevice != null) {
                    log.info("人工开闸使用停车场级回退: laneId={}, parkingLotId={}, fallbackDeviceId={}, deviceSn={}",
                            laneId, lane.getLotId(), gateDevice.getId(), gateDevice.getDeviceSn());
                }
            }
        }

        if (gateDevice == null) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("未找到车道对应的 GATE 或 CAMERA 设备，无法开闸");
            result.setResultMessage("人工开闸失败: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_DEVICE_NOT_FOUND");
            log.warn("人工开闸失败: 车道无可控设备, laneId={}, operatorId={}", laneId, operatorId);
            return result;
        }

        // 4. 通过 DeviceService.openGate() 执行开闸
        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceService.openGate(gateDevice.getId(), "人工开闸: " + reason,
                    isCharge ? plateNumber : null, isCharge ? feeCents : null);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(null);
            if (success) {
                result.setGateResult("人工开闸成功");
                result.setResultMessage("人工开闸: " + reason);
                result.setException(false);
                log.info("人工开闸成功: laneId={}, operatorId={}, deviceId={}, deviceSn={}, reason={}",
                        laneId, operatorId, gateDevice.getId(), gateDevice.getDeviceSn(), reason);

                // GAP-03: 人工开闸补写 parking_session（entry_trigger=manual_open）
                try {
                    ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
                    if (lane != null && plateNumber != null && !plateNumber.isEmpty()) {
                        ParkingSessionEntryCmd entryCmd = new ParkingSessionEntryCmd();
                        entryCmd.setParkingLotId(lane.getLotId());
                        // 岗亭/平台用户 tenantId 为 null，从车道继承租户，确保 session 落库
                        entryCmd.setTenantId(lane.getTenantId());
                        entryCmd.setLaneId(laneId);
                        entryCmd.setPlateNumber(plateNumber.toUpperCase());
                        entryCmd.setVehicleType("TEMP");
                        entryCmd.setEntryOperator(operatorId);
                        entryCmd.setEntryTrigger(ParkingSession.TRIGGER_MANUAL_OPEN);
                        entryCmd.setRemark("岗亭人工放行: " + reason);
                        parkingSessionService.entry(entryCmd);
                        log.info("人工开闸已补写 parking_session: plate={}, laneId={}, operatorId={}",
                                plateNumber, laneId, operatorId);
                    }
                } catch (Exception e) {
                    // 补写失败不阻塞开闸结果
                    log.warn("人工开闸补写 parking_session 失败: plate={}, laneId={}, error={}",
                            plateNumber, laneId, e.getMessage());
                }
            } else {
                result.setGateResult("人工开闸失败: " + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                result.setResultMessage("人工开闸失败: " + reason);
                result.setException(true);
                result.setExceptionType("GATE_OPEN_FAILED");
                log.warn("人工开闸设备返回失败: laneId={}, operatorId={}, deviceId={}, deviceSn={}, message={}",
                        laneId, operatorId, gateDevice.getId(), gateDevice.getDeviceSn(), gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("人工开闸异常（UNCERTAIN）: " + e.getMessage());
            result.setResultMessage("人工开闸异常: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_OPEN_UNCERTAIN");
            log.error("人工开闸异常（UNCERTAIN）: laneId={}, operatorId={}, deviceId={}, deviceSn={}, error={}",
                    laneId, operatorId, gateDevice.getId(), gateDevice.getDeviceSn(), e.getMessage());
        }

        log.info("人工开闸审计: laneId={}, operatorId={}, reason={}, isCharge={}, feeCents={}, plateNumber={}, deviceId={}, deviceSn={}, "
                + "gateCommandSent={}, gateDeviceAck={}, gateOpened={}",
                laneId, operatorId, reason, isCharge, feeCents, plateNumber, gateDevice.getId(), gateDevice.getDeviceSn(),
                result.getGateCommandSent(), result.getGateDeviceAck(), result.getGateOpened());

        return result;
    }

    @Override
    public RecognitionResultVO manualCloseGate(Long laneId, Long operatorId, String reason) {
        log.info("人工关闸请求: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);

        // 与开闸共享同一设备查找逻辑
        Device gateDevice = deviceMapper.selectByLaneIdAndTypeIgnoreTenant(laneId, "GATE");
        if (gateDevice == null) {
            Device camera = deviceMapper.selectByLaneIdAndTypeIgnoreTenant(laneId, "CAMERA");
            if (camera != null && camera.getCapabilities() != null
                    && camera.getCapabilities().contains("OPEN_GATE")) {
                gateDevice = camera;
            }
        }
        if (gateDevice == null) {
            ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
            if (lane != null && lane.getLotId() != null) {
                gateDevice = deviceMapper.selectCameraWithOpenGateByLotIdIgnoreTenant(lane.getLotId());
                if (gateDevice != null) {
                    log.info("人工关闸使用停车场级回退: laneId={}, parkingLotId={}, fallbackDeviceId={}, deviceSn={}",
                            laneId, lane.getLotId(), gateDevice.getId(), gateDevice.getDeviceSn());
                }
            }
        }

        if (gateDevice == null) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("未找到车道对应的 GATE 或 CAMERA 设备，无法关闸");
            result.setResultMessage("人工关闸失败: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_DEVICE_NOT_FOUND");
            log.warn("人工关闸失败: 车道无可控设备, laneId={}, operatorId={}", laneId, operatorId);
            return result;
        }

        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceService.closeGate(gateDevice.getId(), "人工关闸: " + reason);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(null);
            if (success) {
                result.setGateResult("人工关闸成功");
                result.setResultMessage("人工关闸: " + reason);
                result.setException(false);
                log.info("人工关闸成功: laneId={}, operatorId={}, deviceId={}, deviceSn={}, reason={}",
                        laneId, operatorId, gateDevice.getId(), gateDevice.getDeviceSn(), reason);
            } else {
                result.setGateResult("人工关闸失败: " + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                result.setResultMessage("人工关闸失败: " + reason);
                result.setException(true);
                result.setExceptionType("GATE_CLOSE_FAILED");
                log.warn("人工关闸设备返回失败: laneId={}, operatorId={}, deviceId={}, deviceSn={}, message={}",
                        laneId, operatorId, gateDevice.getId(), gateDevice.getDeviceSn(), gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("人工关闸异常（UNCERTAIN）: " + e.getMessage());
            result.setResultMessage("人工关闸异常: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_CLOSE_UNCERTAIN");
            log.error("人工关闸异常（UNCERTAIN）: laneId={}, operatorId={}, deviceId={}, deviceSn={}, error={}",
                    laneId, operatorId, gateDevice.getId(), gateDevice.getDeviceSn(), e.getMessage());
        }

        log.info("人工关闸审计: laneId={}, operatorId={}, reason={}, deviceId={}, deviceSn={}, "
                + "gateCommandSent={}, gateDeviceAck={}, gateOpened={}",
                laneId, operatorId, reason, gateDevice.getId(), gateDevice.getDeviceSn(),
                result.getGateCommandSent(), result.getGateDeviceAck(), result.getGateOpened());

        return result;
    }

    @Override
    public RecognitionResultVO manualLockGate(Long laneId, Long operatorId, String reason) {
        log.info("常开（锁定道闸）请求: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);

        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceService.lockGateByLane(laneId, "常开（锁定道闸）: " + reason);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(true);
            if (success) {
                result.setGateResult("常开成功（道闸已锁定）");
                result.setResultMessage("常开: " + reason);
                result.setException(false);
                log.info("常开成功: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

                // GAP-01: lockGate 成功后同步落库 gate_mode = ALWAYS_OPEN
                try {
                    ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
                    if (lane != null && !ParkingLane.GATE_MODE_ALWAYS_OPEN.equals(lane.getGateMode())) {
                        lane.setGateMode(ParkingLane.GATE_MODE_ALWAYS_OPEN);
                        lane.setUpdatedAt(LocalDateTime.now());
                        parkingLaneMapper.updateById(lane);
                        log.info("常开成功，gate_mode 已更新为 ALWAYS_OPEN: laneId={}", laneId);
                    }
                } catch (Exception e) {
                    log.warn("常开成功但 gate_mode 落库失败: laneId={}, error={}", laneId, e.getMessage());
                }
            } else {
                result.setGateResult("常开失败: " + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                result.setResultMessage("常开失败: " + reason);
                result.setException(true);
                result.setExceptionType("GATE_LOCK_FAILED");
                log.warn("常开设备返回失败: laneId={}, operatorId={}, message={}",
                        laneId, operatorId, gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("常开异常（UNCERTAIN）: " + e.getMessage());
            result.setResultMessage("常开异常: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_LOCK_UNCERTAIN");
            log.error("常开异常（UNCERTAIN）: laneId={}, operatorId={}, error={}",
                    laneId, operatorId, e.getMessage());
        }

        return result;
    }

    @Override
    public RecognitionResultVO manualUnlockGate(Long laneId, Long operatorId, String reason) {
        log.info("取消常开（解除道闸锁定）请求: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);

        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceService.unlockGateByLane(laneId, "取消常开（解除锁定）: " + reason);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(false);
            if (success) {
                result.setGateResult("取消常开成功（道闸已解锁并关闸）");
                result.setResultMessage("取消常开: " + reason);
                result.setException(false);
                log.info("取消常开成功: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

                // GAP-01: unlockGate 成功后同步落库 gate_mode = AUTO
                try {
                    ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
                    if (lane != null && !ParkingLane.GATE_MODE_AUTO.equals(lane.getGateMode())) {
                        lane.setGateMode(ParkingLane.GATE_MODE_AUTO);
                        lane.setUpdatedAt(LocalDateTime.now());
                        parkingLaneMapper.updateById(lane);
                        log.info("取消常开成功，gate_mode 已恢复为 AUTO: laneId={}", laneId);
                    }
                } catch (Exception e) {
                    log.warn("取消常开成功但 gate_mode 落库失败: laneId={}, error={}", laneId, e.getMessage());
                }
            } else {
                result.setGateResult("取消常开失败: " + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                result.setResultMessage("取消常开失败: " + reason);
                result.setException(true);
                result.setExceptionType("GATE_UNLOCK_FAILED");
                log.warn("取消常开设备返回失败: laneId={}, operatorId={}, message={}",
                        laneId, operatorId, gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("取消常开异常（UNCERTAIN）: " + e.getMessage());
            result.setResultMessage("取消常开异常: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_UNLOCK_UNCERTAIN");
            log.error("取消常开异常（UNCERTAIN）: laneId={}, operatorId={}, error={}",
                    laneId, operatorId, e.getMessage());
        }

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

        // 确定入场触发方式（V1.4: 先检查 gate_mode，常开时使用 always_open_period）
        ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(cmd.getLaneId());
        String gateMode = (lane != null) ? lane.getGateMode() : ParkingLane.GATE_MODE_AUTO;

        String entryTrigger;
        if (ParkingLane.GATE_MODE_ALWAYS_OPEN.equals(gateMode)) {
            entryTrigger = ParkingSession.TRIGGER_ALWAYS_OPEN_PERIOD;
        } else {
            entryTrigger = determineEntryTrigger(decision);
        }

        // 创建入场记录
        ParkingSessionEntryCmd entryCmd = new ParkingSessionEntryCmd();
        entryCmd.setParkingLotId(cmd.getParkingLotId());
        entryCmd.setLaneId(cmd.getLaneId());
        entryCmd.setPlateNumber(cmd.getPlateNumber());
        entryCmd.setPlateColor(cmd.getPlateColor());
        entryCmd.setVehicleType(decision.getVehicleType());
        entryCmd.setEntryImage(cmd.getCaptureImage());
        entryCmd.setEntryTrigger(entryTrigger);

        var sessionVO = parkingSessionService.entry(entryCmd);
        result.setSessionId(sessionVO.getId());

        result.setAllowPass(true);
        result.setFeeAmount(BigDecimal.ZERO);

        // GAP-01: 检查车道 gate_mode
        // - ALWAYS_CLOSE: 白名单命中不下发开闸，事件照常推送，手动开闸仍可用
        // - ALWAYS_OPEN: 白名单命中不下发开闸（闸已常开），entryTrigger 已设为 always_open_period
        // - AUTO: 白名单命中自动开闸
        if (ParkingLane.GATE_MODE_ALWAYS_CLOSE.equals(gateMode)) {
            log.info("车道常关模式，白名单车辆不下发开闸: plate={}, laneId={}, gateMode={}",
                    cmd.getPlateNumber(), cmd.getLaneId(), gateMode);
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("车道常关模式，未自动开闸");
        } else if (ParkingLane.GATE_MODE_ALWAYS_OPEN.equals(gateMode)) {
            log.info("车道常开模式，闸已常开无需再次下发: plate={}, laneId={}",
                    cmd.getPlateNumber(), cmd.getLaneId());
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(true);
            result.setGateResult("车道常开模式");
        } else {
            // AUTO 模式：白名单命中自动开闸
            executeGateOpen(cmd.getParkingLotId(), cmd.getLaneId(), result, "ENTRY", cmd.getPlateNumber());
        }

        result.setResultMessage(decision.getDecisionReason() + "，在场记录已创建");
        result.setException(false);

        log.info("入场处理完成: plate={}, sessionId={}, type={}, entryTrigger={}, gateMode={}, gateOpened={}",
                cmd.getPlateNumber(), sessionVO.getId(), decision.getVehicleType(),
                entryTrigger, gateMode, result.getGateOpened());

        return result;
    }

    /**
     * 根据车辆类型判定结果确定入场触发方式（GAP-04）。
     */
    private String determineEntryTrigger(VehicleTypeDecisionVO decision) {
        if ("WHITE".equals(decision.getVehicleType())) {
            return ParkingSession.TRIGGER_WHITELIST_AUTO;
        }
        // 其他类型（月卡/固定车位）一期虽不启用但保留分支
        return null;
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
     * 优先查找车道绑定的 GATE 设备并通过 Device Access 开闸。
     * 若车道无 GATE 设备但有 CAMERA 设备（如臻识 C5H），则回退到 CAMERA 设备开闸。
     * 所有设备统一通过 Device Access → Adapter 下发开闸命令，Adapter 根据设备类型
     * 自动选择 gate_direct_open 或 gpio_out 协议。
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
        // 1. 先查找 GATE 设备
        Device gateDevice = findGateDevice(laneId);

        // 2. 若无 GATE 设备，查找带 OPEN_GATE 能力的 CAMERA 设备（如臻识 C5H GPIO 控制）
        if (gateDevice == null) {
            gateDevice = findCameraWithGateCapability(laneId);
        }

        if (gateDevice == null) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("未找到车道对应的 GATE 或 CAMERA 设备");
            log.warn("{}开闸跳过: 车道无可控设备, laneId={}, plate={}", direction, laneId, plateNumber);
            Long tenantId = TenantContext.getTenantId();
            monitorAlertService.createGateAlert(tenantId, parkingLotId, laneId, null,
                    "DEVICE_CONFIG_MISSING",
                    "开闸跳过: 车道无可控设备, laneId=" + laneId + ", plate=" + plateNumber);
            return;
        }

        String deviceSn = gateDevice.getDeviceSn();

        // 3. 统一通过 Device Access 开闸（Adapter 根据设备类型自动选择 gate_direct_open 或 gpio_out 协议）
        executeDaGateOpen(deviceSn, result, direction, plateNumber);

        // 记录 UNCERTAIN 告警（gateOpened=null 且设备未确认时；成功不开误报告警）
        if (result.getGateOpened() == null && result.getGateCommandSent() == Boolean.TRUE
                && Boolean.FALSE.equals(result.getGateDeviceAck())) {
            Long tenantId = TenantContext.getTenantId();
            monitorAlertService.createGateAlert(tenantId, parkingLotId, laneId, deviceSn,
                    "DEVICE_UNCERTAIN",
                    direction + "开闸异常（UNCERTAIN）: " + result.getGateResult());
        }
    }

    /**
     * 通过 Device Access 开闸（统一路径，任务包 7-1）。
     * <p>
     * Adapter 根据设备类型自动选择 gate_direct_open（GATE 设备）或
     * gpio_out（CAMERA GPIO 控制）协议。
     * 携带 commandId 支持网络瞬断场景的自动重试。
     */
    private void executeDaGateOpen(String deviceSn, RecognitionResultVO result,
                                    String direction, String plateNumber) {
        String commandId = UUID.randomUUID().toString();
        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceAccessClient.openGate(deviceSn, commandId);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(null); // 一期无法确认闸杆实际状态
            if (success) {
                result.setGateResult(direction + "开闸成功");
            } else {
                result.setGateResult(direction + "开闸失败: "
                        + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                log.warn("{}开闸设备返回失败: plate={}, deviceSn={}, commandId={}, deviceCode={}, message={}",
                        direction, plateNumber, deviceSn, commandId, gateResult.getDeviceCode(), gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult(direction + "开闸异常（UNCERTAIN）: " + e.getMessage());
            log.error("{}开闸异常（UNCERTAIN）: plate={}, deviceSn={}, commandId={}, error={}",
                    direction, plateNumber, deviceSn, commandId, e.getMessage());
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

    /**
     * 查找车道绑定的具有 OPEN_GATE 能力的 CAMERA 设备。
     * <p>
     * 臻识 C5H 等相机通过 GPIO 直接控制道闸，设备能力中需包含 OPEN_GATE。
     *
     * @param laneId 车道 ID
     * @return CAMERA 设备实体，未找到返回 null
     */
    private Device findCameraWithGateCapability(Long laneId) {
        if (laneId == null) {
            return null;
        }
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getLaneId, laneId)
                        .eq(Device::getDeviceType, "CAMERA")
                        .eq(Device::getStatus, "ENABLED")
                        .last("LIMIT 1"));
        if (devices.isEmpty()) {
            return null;
        }
        Device camera = devices.get(0);
        // 检查 capabilities 是否包含 OPEN_GATE
        if (camera.getCapabilities() != null && camera.getCapabilities().contains("OPEN_GATE")) {
            return camera;
        }
        return null;
    }
}
