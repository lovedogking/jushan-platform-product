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
import com.jushan.platform.modules.device.client.DeviceAccessClient;
import com.jushan.platform.modules.device.client.dto.CommandResultDTO;
import com.jushan.platform.modules.device.entity.Device;
import com.jushan.platform.modules.booth.entity.RecognitionEventLog;
import com.jushan.platform.modules.account.entity.SysUser;
import com.jushan.platform.modules.account.mapper.SysUserMapper;
import com.jushan.platform.modules.device.mapper.DeviceMapper;
import com.jushan.platform.modules.booth.mapper.RecognitionEventLogMapper;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.service.BillingEngine;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import com.jushan.platform.modules.parking.mapper.ParkingLaneMapper;
import com.jushan.platform.modules.device.service.DeviceService;
import com.jushan.platform.modules.device.service.MonitorAlertService;
import com.jushan.platform.modules.booth.service.GateOperationLogService;
import com.jushan.platform.modules.booth.entity.GateOperationLog;
import com.jushan.platform.modules.booth.ws.BoothWebSocketPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 入场重复识别幂等窗口（秒）：BR-08 同一车道同一车牌 30 秒内去重 */
    private static final int ENTRY_DEDUP_WINDOW_SECONDS = 30;

    /** 出场重复识别幂等窗口（秒）：开闸放行后该窗口内的同车重复识别直接忽略 */
    private static final int EXIT_DEDUP_WINDOW_SECONDS = 300;

    /** 入场事件去重缓存（plate:laneId → 最近处理时间），窗口 30 秒 */
    private final Map<String, LocalDateTime> recentEntryEvents = new ConcurrentHashMap<>();

    /** 出场事件去重缓存（plate:laneId → 最近处理时间），窗口 30 秒 */
    private final Map<String, LocalDateTime> recentExitEvents = new ConcurrentHashMap<>();

    /** 人工开闸抓拍图回溯窗口（分钟）：取该车道该窗口内最近一次识别事件的抓拍图 */
    private static final int MANUAL_OPEN_CAPTURE_WINDOW_MINUTES = 10;

    private final VehicleTypeDecisionService vehicleTypeDecisionService;
    private final ParkingSessionService parkingSessionService;
    private final BillingEngine billingEngine;
    private final DeviceMapper deviceMapper;
    private final DeviceAccessClient deviceAccessClient;
    private final MonitorAlertService monitorAlertService;
    private final DeviceService deviceService;
    private final ParkingLaneMapper parkingLaneMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final RecognitionEventLogMapper recognitionEventLogMapper;
    private final SysUserMapper sysUserMapper;
    private final GateOperationLogService gateOperationLogService;

    public RecognitionEventServiceImpl(VehicleTypeDecisionService vehicleTypeDecisionService,
                                       ParkingSessionService parkingSessionService,
                                       BillingEngine billingEngine,
                                       DeviceMapper deviceMapper,
                                       DeviceAccessClient deviceAccessClient,
                                       MonitorAlertService monitorAlertService,
                                       DeviceService deviceService,
                                       ParkingLaneMapper parkingLaneMapper,
                                       ParkingLotMapper parkingLotMapper,
                                       BoothWebSocketPublisher boothWebSocketPublisher,
                                       RecognitionEventLogMapper recognitionEventLogMapper,
                                       GateOperationLogService gateOperationLogService,
                                       SysUserMapper sysUserMapper) {
        this.vehicleTypeDecisionService = vehicleTypeDecisionService;
        this.parkingSessionService = parkingSessionService;
        this.billingEngine = billingEngine;
        this.deviceMapper = deviceMapper;
        this.deviceAccessClient = deviceAccessClient;
        this.monitorAlertService = monitorAlertService;
        this.deviceService = deviceService;
        this.parkingLaneMapper = parkingLaneMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.recognitionEventLogMapper = recognitionEventLogMapper;
        this.gateOperationLogService = gateOperationLogService;
        this.sysUserMapper = sysUserMapper;
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

        // 0. laneId 守卫：设备台账未绑定车道时拒绝处理，避免 NPE
        if (cmd.getLaneId() == null) {
            result.setAllowPass(false);
            result.setException(true);
            result.setExceptionType("NO_LANE_BINDING");
            result.setResultMessage("设备未绑定车道，无法处理识别事件");
            log.warn("识别事件 laneId 为 null，已拒绝: plate={}, deviceSn={}", plateNumber, cmd.getDeviceSn());
            return result;
        }

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
                                              boolean isCharge, Integer feeCents, String plateNumber,
                                              String entryImage, Integer direction,
                                              String plateColor, String vehicleType) {
        log.info("人工开闸请求: laneId={}, operatorId={}, reason={}, isCharge={}, feeCents={}, plateNumber={}, hasEntryImage={}",
                laneId, operatorId, reason, isCharge, feeCents, plateNumber, entryImage != null);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);

        // 1. 按车道解析控闸设备（统一方法，解析失败直接抛 BusinessException）
        Device gateDevice;
        try {
            gateDevice = deviceService.resolveGateDevice(laneId);
        } catch (BusinessException e) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult(e.getMessage());
            result.setResultMessage("人工开闸失败: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_DEVICE_NOT_FOUND");
            log.warn("人工开闸失败: {}, laneId={}, operatorId={}", e.getMessage(), laneId, operatorId);
            return result;
        }

        // 2. 通过 DeviceService.openGate() 执行开闸
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

                // 开闸成功后播报语音
                try {
                    String voiceText = buildVoiceText(plateNumber, vehicleType, plateColor, gateDevice.getVoiceReleaseTemplate());
                    deviceService.voiceControl(gateDevice.getId(), "PLAY", voiceText, null);
                    log.info("语音播报已发送: deviceId={}, text={}", gateDevice.getId(), voiceText);
                } catch (Exception e) {
                    log.warn("语音播报失败（不影响开闸）: deviceId={}, error={}", gateDevice.getId(), e.getMessage());
                }

                // 推送道闸状态到岗亭
                try {
                    boothWebSocketPublisher.pushGateStatus(gateDevice.getParkingLotId(),
                            laneId, "OPENED");
                } catch (Exception e) {
                    log.warn("道闸状态推送失败（不影响开闸）: laneId={}, error={}",
                            laneId, e.getMessage());
                }

                ParkingLane lane = null;
                try {
                    lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
                } catch (Exception e) {
                    log.warn("人工开闸查询车道失败（不影响开闸结果）: laneId={}, error={}", laneId, e.getMessage());
                }

                // 开闸抓拍：优先使用前端传入的抓拍图，否则取该车道最近识别事件的抓拍图
                RecognitionEventLog captureEvent = null;
                if (lane != null) {
                    if (entryImage != null && !entryImage.isBlank()) {
                        captureEvent = new RecognitionEventLog();
                        captureEvent.setImagePath(entryImage);
                        captureEvent.setPlateImagePath(entryImage);
                        captureEvent.setDeviceId(gateDevice.getId());
                        captureEvent.setEventTime(LocalDateTime.now());
                    } else {
                        captureEvent = findLatestCaptureEvent(lane, plateNumber);
                    }
                }

                // GAP-03: 人工开闸补写 parking_session（entry_trigger=manual_open，附抓拍图）
                //        根据车道方向：入口→写入场记录，出口→写出场记录
                try {
                    if (lane != null && plateNumber != null && !plateNumber.isEmpty()) {
                        boolean isExitLane = (direction != null && direction == 2)
                                || (lane.getType() != null && (lane.getType() == 2 || lane.getType() == 3));
                        if (isExitLane) {
                            // 出口车道：查询在场记录，执行出场
                            var inSession = parkingSessionService.getInByPlateNumber(plateNumber.toUpperCase());
                            if (inSession != null) {
                                ParkingSessionExitCmd exitCmd = new ParkingSessionExitCmd();
                                exitCmd.setSessionId(inSession.getId());
                                exitCmd.setExitLaneId(laneId);
                                exitCmd.setExitOperator(operatorId);
                                exitCmd.setFeeAmount(isCharge && feeCents != null
                                        ? java.math.BigDecimal.valueOf(feeCents).movePointLeft(2)
                                        : java.math.BigDecimal.ZERO);
                                if (captureEvent != null) {
                                    exitCmd.setExitImage(captureEvent.getImagePath());
                                }
                                parkingSessionService.exit(exitCmd);
                                log.info("人工开闸已补写出口记录: plate={}, laneId={}, operatorId={}, exitImage={}",
                                        plateNumber, laneId, operatorId,
                                        captureEvent != null ? captureEvent.getImagePath() : "无");
                            } else {
                                log.warn("人工开闸出口无在场记录，仅开闸不写session: plate={}, laneId={}",
                                        plateNumber, laneId);
                            }
                        } else {
                            // 入口车道：写入场记录
                            ParkingSessionEntryCmd entryCmd = new ParkingSessionEntryCmd();
                            entryCmd.setParkingLotId(lane.getLotId());
                            entryCmd.setTenantId(lane.getTenantId());
                            entryCmd.setLaneId(laneId);
                            entryCmd.setPlateNumber(plateNumber.toUpperCase());
                            // 查询车辆类型而非硬编码 TEMP
                            var decision = vehicleTypeDecisionService.decide(plateNumber, lane.getLotId(), lane.getTenantId());
                            entryCmd.setVehicleType(decision.getVehicleType());
                            entryCmd.setEntryOperator(operatorId);
                            entryCmd.setEntryTrigger(ParkingSession.TRIGGER_MANUAL_OPEN);
                            if (captureEvent != null) {
                                entryCmd.setEntryImage(captureEvent.getImagePath());
                            }
                            if (isCharge && feeCents != null && feeCents > 0) {
                                java.math.BigDecimal fee = java.math.BigDecimal.valueOf(feeCents).movePointLeft(2);
                                entryCmd.setFeeAmount(fee);
                                entryCmd.setPaidAmount(fee);
                            }
                            entryCmd.setRemark("岗亭人工放行: " + reason);
                            parkingSessionService.entry(entryCmd);
                            log.info("人工开闸已补写入场记录: plate={}, laneId={}, operatorId={}, vehicleType={}",
                                    plateNumber, laneId, operatorId, decision.getVehicleType());
                        }
                    }
                } catch (Exception e) {
                    // 补写失败不阻塞开闸结果
                    log.warn("人工开闸补写 session 失败: plate={}, laneId={}, error={}",
                            plateNumber, laneId, e.getMessage());
                }

                // 开闸事件落库 + WebSocket 推送：岗亭车道卡片实时显示本次放行车辆与抓拍图
                if (lane != null) {
                    persistAndPushManualOpenEvent(lane, captureEvent, plateNumber);
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

        // 写操作日志（不影响开闸结果）
        try {
            Long tId = TenantContext.getTenantId();
            GateOperationLog opLog = new GateOperationLog();
            opLog.setTenantId(tId != null ? tId : 1L);
            opLog.setParkingLotId(1L);
            opLog.setLaneId(laneId);
            opLog.setOperationType("OPEN_GATE");
            opLog.setReason(reason);
            opLog.setPlateNumber(plateNumber);
            opLog.setDirection(direction != null ? (direction == 1 ? "ENTRY" : "EXIT") : null);
            opLog.setOperatorId(operatorId);
            try {
                SysUser user = sysUserMapper.selectById(operatorId);
                opLog.setOperatorName(user != null ? user.getUsername() : String.valueOf(operatorId));
            } catch (Exception ignored) {
                opLog.setOperatorName(String.valueOf(operatorId));
            }
            opLog.setFeeCents(isCharge ? (feeCents != null ? feeCents : 0) : 0);
            opLog.setEntryImage(entryImage);
            opLog.setRemark(plateColor != null || vehicleType != null ?
                    (plateColor != null ? plateColor : "") + (vehicleType != null ? "/" + vehicleType : "") : null);
            gateOperationLogService.saveLog(opLog);
        } catch (Exception e) {
            log.warn("记录操作日志失败（不影响开闸）: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 查找人工开闸的抓拍图来源：该车道 {@link #MANUAL_OPEN_CAPTURE_WINDOW_MINUTES} 分钟内
     * 最近一次带抓拍图的识别事件。
     * <p>
     * 平台无「主动命令相机抓拍」的下行能力，相机只在识别到车牌时上报图片，
     * 因此取开闸前车道最近的识别事件图作为本次放行的抓拍图。
     * 优先匹配放行车辆车牌（纠正后车牌也纳入匹配），无匹配时回退到车道最近带图事件。
     *
     * @param lane        车道（用于 laneId 与租户上下文）
     * @param plateNumber 放行车辆车牌（可为空）
     * @return 最近的带图识别事件；无则返回 null
     */
    private RecognitionEventLog findLatestCaptureEvent(ParkingLane lane, String plateNumber) {
        TenantContext.Snapshot previousContext = TenantContext.get();
        try {
            TenantContext.set(new TenantContext.Snapshot(
                    lane.getTenantId(), 0L, "platform", null, null));
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(MANUAL_OPEN_CAPTURE_WINDOW_MINUTES);
            if (plateNumber != null && !plateNumber.isEmpty()) {
                String plate = plateNumber.toUpperCase();
                RecognitionEventLog matched = selectLatestCaptureEvent(lane.getId(), plate, threshold);
                if (matched != null) {
                    return matched;
                }
            }
            return selectLatestCaptureEvent(lane.getId(), null, threshold);
        } catch (Exception e) {
            log.warn("人工开闸抓拍图查询失败（忽略）: laneId={}, error={}", lane.getId(), e.getMessage());
            return null;
        } finally {
            restoreTenantContext(previousContext);
        }
    }

    /**
     * 查询车道最近一条带抓拍图的识别事件（plate 为空时不限车牌，同时匹配纠正后车牌）。
     */
    private RecognitionEventLog selectLatestCaptureEvent(Long laneId, String plate, LocalDateTime threshold) {
        LambdaQueryWrapper<RecognitionEventLog> wrapper = new LambdaQueryWrapper<RecognitionEventLog>()
                .eq(RecognitionEventLog::getLaneId, laneId)
                .isNotNull(RecognitionEventLog::getImagePath)
                .ne(RecognitionEventLog::getImagePath, "")
                .ge(RecognitionEventLog::getEventTime, threshold)
                .orderByDesc(RecognitionEventLog::getEventTime)
                .last("LIMIT 1");
        if (plate != null) {
            wrapper.and(w -> w.eq(RecognitionEventLog::getPlateNumber, plate)
                    .or().eq(RecognitionEventLog::getCorrectedPlate, plate));
        }
        List<RecognitionEventLog> list = recognitionEventLogMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 人工开闸事件落库（source=MANUAL）并推送 WebSocket。
     * <p>
     * 岗亭端「最近识别事件」快照与 WS 事件流的数据源即 recognition_event_log，
     * 落库 + 推送后车道卡片立即显示本次放行车辆与抓拍图。
     * 任何失败均不阻塞开闸结果。
     */
    private void persistAndPushManualOpenEvent(ParkingLane lane, RecognitionEventLog captureEvent,
                                               String plateNumber) {
        TenantContext.Snapshot previousContext = TenantContext.get();
        try {
            TenantContext.set(new TenantContext.Snapshot(
                    lane.getTenantId(), 0L, "platform", null, null));

            RecognitionEventLog eventLog = new RecognitionEventLog();
            eventLog.setEventId("MANUAL-" + UUID.randomUUID());
            eventLog.setTenantId(lane.getTenantId());
            eventLog.setParkingLotId(lane.getLotId());
            eventLog.setLaneId(lane.getId());
            eventLog.setDeviceId(captureEvent != null ? captureEvent.getDeviceId() : null);
            String plate = (plateNumber != null && !plateNumber.isEmpty()) ? plateNumber.toUpperCase() : null;
            eventLog.setPlateNumber(plate);
            eventLog.setStandardizedPlate(plate);
            eventLog.setDirection(resolveManualOpenDirection(lane, captureEvent));
            eventLog.setEventTime(LocalDateTime.now());
            if (captureEvent != null) {
                eventLog.setImagePath(captureEvent.getImagePath());
                eventLog.setPlateImagePath(captureEvent.getPlateImagePath());
                eventLog.setConfidence(captureEvent.getConfidence());
            }
            eventLog.setSource("MANUAL");
            eventLog.setStatus("PROCESSED");
            eventLog.setTempPlateFlag(0);
            eventLog.setCreatedAt(LocalDateTime.now());
            recognitionEventLogMapper.insert(eventLog);

            boothWebSocketPublisher.sendRecognitionEvent(lane.getLotId(), eventLog);
            log.info("人工开闸事件已落库并推送: laneId={}, plate={}, hasImage={}",
                    lane.getId(), plate, captureEvent != null);
        } catch (Exception e) {
            log.warn("人工开闸事件落库/推送失败（不影响开闸结果）: laneId={}, error={}",
                    lane.getId(), e.getMessage());
        } finally {
            restoreTenantContext(previousContext);
        }
    }

    /**
     * 推导人工开闸事件方向：按车道类型（1=入口 2=出口），混合车道沿用抓拍事件方向。
     */
    private String resolveManualOpenDirection(ParkingLane lane, RecognitionEventLog captureEvent) {
        if (lane.getType() != null && lane.getType() != 3) {
            return lane.getType() == 1 ? "ENTRY" : "EXIT";
        }
        return captureEvent != null ? captureEvent.getDirection() : null;
    }

    /**
     * 恢复之前的租户上下文（参照 DeviceWebhookService 的模式）。
     */
    private void restoreTenantContext(TenantContext.Snapshot previousContext) {
        if (previousContext != null) {
            TenantContext.set(previousContext);
        } else {
            TenantContext.clear();
        }
    }

    @Override
    public RecognitionResultVO manualCloseGate(Long laneId, Long operatorId, String reason) {
        log.info("人工关闸请求: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);

        // 按车道解析控闸设备（统一方法，解析失败直接抛 BusinessException）
        Device gateDevice;
        try {
            gateDevice = deviceService.resolveGateDevice(laneId);
        } catch (BusinessException e) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult(e.getMessage());
            result.setResultMessage("人工关闸失败: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_DEVICE_NOT_FOUND");
            log.warn("人工关闸失败: {}, laneId={}, operatorId={}", e.getMessage(), laneId, operatorId);
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
        return unlockGateInternal(laneId, operatorId, reason,
                "取消常开", "GATE_UNLOCK_FAILED", "GATE_UNLOCK_UNCERTAIN");
    }

    @Override
    public RecognitionResultVO manualLockCloseGate(Long laneId, Long operatorId, String reason) {
        log.info("常关（锁定道闸关闭）请求: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(false);

        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceService.lockCloseGateByLane(laneId, "常关（锁定道闸关闭）: " + reason);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(false);
            if (success) {
                result.setGateResult("常关成功（道闸已锁定关闭）");
                result.setResultMessage("常关: " + reason);
                result.setException(false);
                log.info("常关成功: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);

                // 常关成功后同步落库 gate_mode = ALWAYS_CLOSE
                try {
                    ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
                    if (lane != null && !ParkingLane.GATE_MODE_ALWAYS_CLOSE.equals(lane.getGateMode())) {
                        lane.setGateMode(ParkingLane.GATE_MODE_ALWAYS_CLOSE);
                        lane.setUpdatedAt(LocalDateTime.now());
                        parkingLaneMapper.updateById(lane);
                        log.info("常关成功，gate_mode 已更新为 ALWAYS_CLOSE: laneId={}", laneId);
                    }
                } catch (Exception e) {
                    log.warn("常关成功但 gate_mode 落库失败: laneId={}, error={}", laneId, e.getMessage());
                }
            } else {
                result.setGateResult("常关失败: " + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                result.setResultMessage("常关失败: " + reason);
                result.setException(true);
                result.setExceptionType("GATE_LOCK_CLOSE_FAILED");
                log.warn("常关设备返回失败: laneId={}, operatorId={}, message={}",
                        laneId, operatorId, gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult("常关异常（UNCERTAIN）: " + e.getMessage());
            result.setResultMessage("常关异常: " + reason);
            result.setException(true);
            result.setExceptionType("GATE_LOCK_CLOSE_UNCERTAIN");
            log.error("常关异常（UNCERTAIN）: laneId={}, operatorId={}, error={}",
                    laneId, operatorId, e.getMessage());
        }

        return result;
    }

    @Override
    public RecognitionResultVO manualUnlockCloseGate(Long laneId, Long operatorId, String reason) {
        // 底层与取消常开完全一致（都调 unlockGate），通过独立端点区分审计语义
        return unlockGateInternal(laneId, operatorId, reason,
                "取消常关", "GATE_UNLOCK_CLOSE_FAILED", "GATE_UNLOCK_CLOSE_UNCERTAIN");
    }

    /**
     * 解锁道闸的统一实现：调 unlockGateByLane，成功后落库 gate_mode = AUTO。
     * manualUnlockGate 和 manualUnlockCloseGate 的差异仅在于审计文案和 exceptionType，
     * 提取到此方法避免重复。
     */
    private RecognitionResultVO unlockGateInternal(Long laneId, Long operatorId, String reason,
                                                    String actionLabel, String failType, String uncertainType) {
        log.info("{}（解除道闸锁定）请求: laneId={}, operatorId={}, reason={}", actionLabel, laneId, operatorId, reason);

        RecognitionResultVO result = new RecognitionResultVO();
        result.setAllowPass(true);

        try {
            result.setGateCommandSent(true);
            CommandResultDTO gateResult = deviceService.unlockGateByLane(laneId, actionLabel + "（解除锁定）: " + reason);
            boolean success = gateResult.isSuccessful();
            result.setGateDeviceAck(success);
            result.setGateOpened(false);
            if (success) {
                result.setGateResult(actionLabel + "成功（道闸已解锁，恢复正常模式）");
                result.setResultMessage(actionLabel + ": " + reason);
                result.setException(false);
                log.info("{}成功: laneId={}, operatorId={}, reason={}", actionLabel, laneId, operatorId, reason);

                try {
                    ParkingLane lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
                    if (lane != null && !ParkingLane.GATE_MODE_AUTO.equals(lane.getGateMode())) {
                        lane.setGateMode(ParkingLane.GATE_MODE_AUTO);
                        lane.setUpdatedAt(LocalDateTime.now());
                        parkingLaneMapper.updateById(lane);
                        log.info("{}成功，gate_mode 已恢复为 AUTO: laneId={}", actionLabel, laneId);
                    }
                } catch (Exception e) {
                    log.warn("{}成功但 gate_mode 落库失败: laneId={}, error={}", actionLabel, laneId, e.getMessage());
                }
            } else {
                result.setGateResult(actionLabel + "失败: " + (gateResult.getMessage() != null ? gateResult.getMessage() : "设备返回异常"));
                result.setResultMessage(actionLabel + "失败: " + reason);
                result.setException(true);
                result.setExceptionType(failType);
                log.warn("{}设备返回失败: laneId={}, operatorId={}, message={}",
                        actionLabel, laneId, operatorId, gateResult.getMessage());
            }
        } catch (BusinessException e) {
            result.setGateCommandSent(true);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult(actionLabel + "异常（UNCERTAIN）: " + e.getMessage());
            result.setResultMessage(actionLabel + "异常: " + reason);
            result.setException(true);
            result.setExceptionType(uncertainType);
            log.error("{}异常（UNCERTAIN）: laneId={}, operatorId={}, error={}",
                    actionLabel, laneId, operatorId, e.getMessage());
        }

        return result;
    }

    private RecognitionResultVO handleEntry(RecognitionEventCmd cmd, VehicleTypeDecisionVO decision,
                                           RecognitionResultVO result, Long tenantId) {
        // 入场幂等检查：同一车道同一车牌 30 秒内不重复处理
        cleanupExpiredEntryEvents();
        String entryDedupKey = cmd.getPlateNumber() + ":" + cmd.getLaneId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastEntry = recentEntryEvents.compute(entryDedupKey, (k, v) -> {
            if (v != null && v.plusSeconds(ENTRY_DEDUP_WINDOW_SECONDS).isAfter(now)) {
                return v;
            }
            return now;
        });
        if (!lastEntry.equals(now)) {
            log.info("入场重复识别幂等忽略: plate={}, laneId={}", cmd.getPlateNumber(), cmd.getLaneId());
            result.setAllowPass(false);
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setResultMessage("重复识别，已忽略");
            result.setException(false);
            return result;
        }

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

        // 推送车位变化到岗亭（与存量 EntryService 行为对齐）
        pushSpaceUpdate(cmd.getParkingLotId());

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
        // 出场幂等检查：同一车道同一车牌 30 秒内不重复处理
        cleanupExpiredExitEvents();
        String exitDedupKey = cmd.getPlateNumber() + ":" + cmd.getLaneId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastExit = recentExitEvents.compute(exitDedupKey, (k, v) -> {
            if (v != null && v.plusSeconds(EXIT_DEDUP_WINDOW_SECONDS).isAfter(now)) {
                return v;
            }
            return now;
        });
        if (!lastExit.equals(now)) {
            log.info("出场重复识别幂等忽略: plate={}, laneId={}", cmd.getPlateNumber(), cmd.getLaneId());
            result.setAllowPass(false);
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setResultMessage("重复识别，已忽略");
            result.setException(false);
            result.setDuplicateIgnored(true);
            return result;
        }

        // 查询在场记录
        var sessionVO = parkingSessionService.getInByPlateNumber(cmd.getPlateNumber());
        if (sessionVO == null) {
            // 出场幂等：开闸放行后相机持续上报同一车辆，最近已成功出场的重复识别直接忽略，
            // 不重复开闸、不重复计费、不报异常
            var recentOut = parkingSessionService.getRecentOutByPlateAndLot(
                    cmd.getPlateNumber(), cmd.getParkingLotId(), EXIT_DEDUP_WINDOW_SECONDS);
            if (recentOut != null) {
                result.setAllowPass(false);
                result.setGateCommandSent(false);
                result.setGateDeviceAck(false);
                result.setGateOpened(null);
                result.setSessionId(recentOut.getId());
                result.setResultMessage("重复出场识别，已幂等忽略");
                result.setException(false);
                result.setDuplicateIgnored(true);
                log.info("重复出场识别，幂等忽略: plate={}, laneId={}, sessionId={}",
                        cmd.getPlateNumber(), cmd.getLaneId(), recentOut.getId());
                return result;
            }
            result.setAllowPass(false);
            result.setException(true);
            result.setExceptionType("NO_ENTRY_RECORD");
            result.setResultMessage("未找到入场记录，无法出场");
            log.warn("出场异常: plate={}, 无入场记录", cmd.getPlateNumber());
            return result;
        }

        // 根据车辆类型判定是否允许自动出场
        if (!Boolean.TRUE.equals(decision.getAllowExit())) {
            result.setAllowPass(false);
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setSessionId(sessionVO.getId());
            result.setResultMessage("非白名单车辆，需岗亭人工放行");
            result.setException(true);
            result.setExceptionType("EXIT_DENIED");
            log.warn("出场不自动放行: plate={}, type={}, laneId={}",
                    cmd.getPlateNumber(), decision.getVehicleType(), cmd.getLaneId());
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

        // 推送车位变化到岗亭（与存量 ExitService 行为对齐）
        pushSpaceUpdate(cmd.getParkingLotId());

        return result;
    }

    // ==================== 车位变化推送 ====================

    /**
     * 推送车位变化到岗亭前端（与存量 EntryService/ExitService 行为对齐）。
     * <p>
     * Webhook 识别驱动的入场/出场同样改变在场车辆数，
     * 不推送会导致岗亭「剩余车位/在场车辆」长期不刷新。
     */
    private void pushSpaceUpdate(Long parkingLotId) {
        try {
            ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
            if (lot == null) {
                return;
            }
            int totalSpaces = lot.getTotalSpaces() != null ? lot.getTotalSpaces() : 0;
            int currentVehicles = (int) parkingSessionService.countInByParkingLotIdIgnoreTenant(parkingLotId);
            boothWebSocketPublisher.sendSpaceUpdate(
                    parkingLotId,
                    Math.max(0, totalSpaces - currentVehicles),
                    currentVehicles,
                    totalSpaces);
        } catch (Exception e) {
            log.warn("识别事件车位变化推送失败（不影响主业务）: parkingLotId={}, error={}",
                    parkingLotId, e.getMessage());
        }
    }

    // ==================== 开闸辅助方法 ====================

    /**
     * 执行开闸操作，填充三层状态到 result。
     * <p>
     * 优先查找车道绑定的 GATE 设备并通过 Device Access 开闸。
     * 若车道无 GATE 设备但有 CAMERA 设备（如臻识 C5），则回退到 CAMERA 设备开闸。
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
        // 按车道解析控闸设备（统一方法，解析失败抛 BusinessException）
        Device gateDevice;
        try {
            gateDevice = deviceService.resolveGateDevice(laneId);
        } catch (BusinessException e) {
            result.setGateCommandSent(false);
            result.setGateDeviceAck(false);
            result.setGateOpened(null);
            result.setGateResult(e.getMessage());
            log.warn("{}开闸跳过: {}", direction, e.getMessage());
            Long tenantId = TenantContext.getTenantId();
            monitorAlertService.createGateAlert(tenantId, parkingLotId, laneId, null,
                    "DEVICE_CONFIG_MISSING",
                    e.getMessage() + ", plate=" + plateNumber);
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
     * 清理入场去重缓存中超过 1 分钟的旧记录。
     */
    private void cleanupExpiredEntryEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        recentEntryEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    /**
     * 清理出场去重缓存中超过 1 分钟的旧记录。
     */
    private void cleanupExpiredExitEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        recentExitEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    @Override
    public com.jushan.platform.modules.device.client.dto.CaptureResultDTO captureImage(Long laneId, Integer direction) {
        int dir = (direction != null) ? direction : DeviceService.DIRECTION_ENTRY;
        log.info("主动抓拍请求: laneId={}, direction={}", laneId, dir);

        ParkingLane lane = null;
        try {
            lane = parkingLaneMapper.selectByIdIgnoreTenant(laneId);
        } catch (Exception e) {
            log.warn("主动抓拍查询车道失败: laneId={}, error={}", laneId, e.getMessage());
        }
        if (lane == null) {
            throw new BusinessException(com.jushan.common.CommonErrorCode.PARAM_ERROR,
                    "车道不存在: laneId=" + laneId);
        }

        // 按指定方向解析主相机；direction 为空时默认入口（向后兼容）
        Device camera = deviceMapper.selectPrimaryCameraByLaneAndDirectionIgnoreTenant(laneId, dir);
        if (camera == null) {
            String dirLabel = (dir == DeviceService.DIRECTION_ENTRY) ? "入口" : "出口";
            throw new BusinessException(com.jushan.common.CommonErrorCode.PARAM_ERROR,
                    "车道未绑定" + dirLabel + "相机: laneId=" + laneId);
        }

        String deviceSn = camera.getDeviceSn();
        if (deviceSn == null || deviceSn.isBlank()) {
            throw new BusinessException(com.jushan.common.CommonErrorCode.PARAM_ERROR,
                    "相机设备序列号为空: deviceId=" + camera.getId());
        }

        com.jushan.platform.modules.device.client.dto.CaptureResultDTO result = deviceAccessClient.captureImage(deviceSn);
        log.info("主动抓拍完成: laneId={}, deviceSn={}, success={}, imageUrl={}",
                laneId, deviceSn, result.isSuccessful(), result.getImageUrl());
        return result;
    }

    /** 构建语音播报文本 */
    private String buildVoiceText(String plateNumber, String vehicleType, String plateColor, String template) {
        if (plateNumber == null || plateNumber.isEmpty()) return "请通行";
        String clean = plateNumber.toUpperCase().trim();
        String typeLabel = "临时车";
        if ("MONTHLY".equals(vehicleType)) typeLabel = "月租车";
        else if ("PREPAID".equals(vehicleType)) typeLabel = "储值车";
        else if ("FREE".equals(vehicleType)) typeLabel = "免费车";

        // 如果设备配置了自定义模板，使用模板替换
        if (template != null && !template.isBlank()) {
            return template.replace("{plate}", clean).replace("{type}", typeLabel);
        }
        return clean + "," + typeLabel;
    }
}
