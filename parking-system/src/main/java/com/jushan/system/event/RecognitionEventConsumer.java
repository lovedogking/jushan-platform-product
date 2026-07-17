package com.jushan.system.event;

import com.jushan.common.BusinessException;
import com.jushan.framework.mq.MessageEnvelope;
import com.jushan.framework.mq.MessageIdempotency;
import com.jushan.framework.mq.MqConstants;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.RecognitionEventLogMapper;
import com.jushan.system.mybatis.TenantIgnore;
import com.jushan.system.service.CameraFailoverService;
import com.jushan.system.service.DeviceService;
import com.jushan.system.service.EntryService;
import com.jushan.system.service.ExitService;
import com.jushan.system.service.MonitorAlertService;
import com.jushan.system.service.ParamResolver;
import com.jushan.system.service.TempPlateNumberGenerator;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.ws.BoothWebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 识别事件消费者（T29 校验 + T30 入场）。
 * <p>
 * 从平台内部 RabbitMQ 消费识别事件，执行：
 * <ol>
 *   <li><strong>消息级幂等</strong> — 通过 {@link MessageIdempotency} 检查 messageId，
 *       防止同一条 MQ 消息被重复投递</li>
 *   <li><strong>业务校验</strong> — 设备存在/启用/CAMERA 类型、停车场启用、
 *       车道方向匹配（防御性校验，T28 发布端已验证）</li>
 *   <li><strong>车牌标准化</strong> — 通过 {@link PlateStandardizer} 统一格式</li>
 *   <li><strong>业务级幂等</strong> — eventId UNIQUE 约束保证相同事件只处理一次</li>
 *   <li><strong>T30 入场处理</strong> — ENTRY 事件校验通过后，委托
 *       {@link EntryService} 创建停车记录并更新停车场容量</li>
 *   <li><strong>P004 出场处理</strong> — EXIT 事件校验通过后，委托
 *       {@link ExitService} 匹配停车记录、计算费用、生成订单、决定放行策略并创建出场记录</li>
 *   <li><strong>状态记录</strong> — 更新 recognition_event_log 的处理状态和结果</li>
 * </ol>
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>不信任消息体中的 tenantId/parkingLotId，从数据库可信记录重新推导</li>
 *   <li>ENTRY 事件委托 EntryService 创建停车记录；EXIT 事件委托 ExitService 处理出场流程（P004），不再仅标记 PROCESSED</li>
 * </ul>
 * <p>
 * <strong>异常处理（AUTO ACK 模式）</strong>：
 * <ul>
 *   <li>校验失败 / 重复 eventId / 入场失败 → 捕获、标记 FAILED、不重试（正常 ACK）</li>
 *   <li>系统异常（DB 不可用等）→ 重新抛出，消息进入死信队列</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class RecognitionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecognitionEventConsumer.class);

    /** 幂等标记 TTL：72 小时 */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(72);

    private final MessageIdempotency messageIdempotency;
    private final DeviceMapper deviceMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final RecognitionEventLogMapper eventLogMapper;
    private final EntryService entryService;
    private final ExitService exitService;
    private final CameraFailoverService cameraFailoverService;
    private final TempPlateNumberGenerator tempPlateNumberGenerator;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final MonitorAlertService monitorAlertService;
    private final ParamResolver paramResolver;
    private final ParkingRecordMapper recordMapper;
    private final DeviceService deviceService;

    public RecognitionEventConsumer(MessageIdempotency messageIdempotency,
                                     DeviceMapper deviceMapper,
                                     ParkingLotMapper parkingLotMapper,
                                     ParkingLaneMapper laneMapper,
                                     RecognitionEventLogMapper eventLogMapper,
                                     EntryService entryService,
                                     ExitService exitService,
                                     CameraFailoverService cameraFailoverService,
                                     TempPlateNumberGenerator tempPlateNumberGenerator,
                                     BoothWebSocketPublisher boothWebSocketPublisher,
                                     MonitorAlertService monitorAlertService,
                                     ParamResolver paramResolver,
                                     ParkingRecordMapper recordMapper,
                                     DeviceService deviceService) {
        this.messageIdempotency = messageIdempotency;
        this.deviceMapper = deviceMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.eventLogMapper = eventLogMapper;
        this.entryService = entryService;
        this.exitService = exitService;
        this.cameraFailoverService = cameraFailoverService;
        this.tempPlateNumberGenerator = tempPlateNumberGenerator;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.monitorAlertService = monitorAlertService;
        this.paramResolver = paramResolver;
        this.recordMapper = recordMapper;
        this.deviceService = deviceService;
    }

    /**
     * 消费识别事件（AUTO ACK 模式）。
     * <p>
     * 正常处理完成或可恢复错误被捕获后，框架自动 ACK；
     * 未捕获的 RuntimeException 触发死信机制。
     *
     * @param envelope 消息信封（含 messageId、payload、租户上下文）
     */
    @TenantIgnore(reason = "MQ 识别事件消费：从可信设备记录重新推导租户信息，无需租户拦截")
    @RabbitListener(queues = MqConstants.QUEUE_RECOGNITION_EVENT)
    public void onRecognitionEvent(MessageEnvelope<RecognitionEventPayload> envelope) {
        String messageId = envelope.getMessageId();
        RecognitionEventPayload payload = envelope.getPayload();
        String eventId = payload != null ? payload.getEventId() : "null";

        log.info("收到识别事件: messageId={} eventId={} plate={} direction={}",
                messageId, eventId,
                payload != null ? payload.getPlateNumber() : "null",
                payload != null ? payload.getDirection() : "null");

        // ---- 1. 消息级幂等 ----
        if (messageIdempotency.isProcessed(messageId)) {
            log.info("消息已处理（消息级幂等），跳过: messageId={}", messageId);
            return;
        }

        // ---- 2. 基础校验 ----
        if (payload == null) {
            log.warn("消息体为空，丢弃: messageId={}", messageId);
            messageIdempotency.markProcessed(messageId, IDEMPOTENCY_TTL);
            return;
        }

        try {
            // ---- 3. 业务校验 + 车牌标准化 ----
            ProcessingResult result = validateAndStandardize(payload);

            // ---- 3.5 识别失败接管（无牌车处理） ----
            if (result.recognitionFailed) {
                handleRecognitionFailure(payload, result);
                updateEventLog(payload, result);
                messageIdempotency.markProcessed(messageId, IDEMPOTENCY_TTL);
                return;
            }

            // ---- 4. T30 入场处理 / P004 出场处理 ----
            if (result.success) {
                if ("ENTRY".equals(payload.getDirection())) {
                    try {
                        entryService.handleEntry(payload, result.standardizedPlate);
                    } catch (BusinessException e) {
                        log.warn("入场处理失败: eventId={} plate={} reason={}",
                                eventId, result.standardizedPlate, e.getMessage());
                        result.fail("入场处理失败: " + e.getMessage());
                    }
                } else if ("EXIT".equals(payload.getDirection())) {
                    try {
                        exitService.handleExit(payload, result.standardizedPlate);
                    } catch (BusinessException e) {
                        log.warn("出场处理失败: eventId={} plate={} reason={}",
                                eventId, result.standardizedPlate, e.getMessage());
                        result.fail("出场处理失败: " + e.getMessage());
                    }
                } else {
                    result.fail("未知方向: " + payload.getDirection());
                }
            }

            // ---- 5. 更新事件日志状态 ----
            updateEventLog(payload, result);

            // ---- 6. 标记处理完成 ----
            messageIdempotency.markProcessed(messageId, IDEMPOTENCY_TTL);

            if (result.success) {
                log.info("识别事件处理完成: eventId={} standardizedPlate={}",
                        eventId, result.standardizedPlate);
            } else {
                log.warn("识别事件校验失败: eventId={} reason={}",
                        eventId, result.failureReason);
            }

        } catch (BusinessException e) {
            // 业务异常：校验失败，记录失败原因，不重试
            log.warn("识别事件处理失败（业务异常）: eventId={} code={} message={}",
                    eventId, e.getCode(), e.getMessage());
            updateEventLogFailed(payload, e.getMessage());
            messageIdempotency.markProcessed(messageId, IDEMPOTENCY_TTL);

        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 重复 eventId（业务幂等）：正常现象，跳过
            log.info("事件已存在（业务幂等），跳过: eventId={}", eventId);
            messageIdempotency.markProcessed(messageId, IDEMPOTENCY_TTL);

        // 其他 RuntimeException（如 DB 不可用）→ 重新抛出，消息进入死信队列
        }
    }

    // ==================== 校验与标准化 ====================

    /**
     * 执行完整的业务校验和车牌标准化。
     */
    private ProcessingResult validateAndStandardize(RecognitionEventPayload payload) {
        ProcessingResult result = new ProcessingResult();

        // 3.1 车牌标准化（空牌不立即失败，先完成设备/车场/车道校验后再判断）
        String rawPlate = payload.getPlateNumber();
        boolean plateIsEmpty = (rawPlate == null || rawPlate.isBlank());

        if (!plateIsEmpty) {
            String standardized = PlateStandardizer.normalize(rawPlate);
            if (standardized == null || standardized.isEmpty()) {
                result.fail("车牌号标准化后为空，原始值: "
                        + (rawPlate.length() > 50 ? rawPlate.substring(0, 50) + "..." : rawPlate));
                return result;
            }
            result.standardizedPlate = standardized;
        }

        // 3.2 设备校验
        if (payload.getDeviceId() == null) {
            result.fail("设备 ID 为空");
            return result;
        }

        Device device = deviceMapper.selectByIdIgnoreTenant(payload.getDeviceId());
        if (device == null) {
            result.fail("设备不存在: deviceId=" + payload.getDeviceId());
            return result;
        }
        if (!"ENABLED".equals(device.getStatus())) {
            result.fail("设备已停用: deviceId=" + payload.getDeviceId());
            return result;
        }
        if (!"CAMERA".equals(device.getDeviceType())) {
            result.fail("设备类型不是相机: deviceType=" + device.getDeviceType());
            return result;
        }

        // 3.3 停车场校验（只验证存在性和跨租户/停车场一致性）
        //     停用状态是否允许入场由 EntryService 按 disableNewEntries 配置判断（T30）
        if (device.getParkingLotId() == null) {
            result.fail("设备未绑定停车场: deviceId=" + device.getId());
            return result;
        }

        ParkingLot parkingLot = parkingLotMapper.selectByIdIgnoreTenant(device.getParkingLotId());
        if (parkingLot == null) {
            result.fail("停车场不存在: parkingLotId=" + device.getParkingLotId());
            return result;
        }

        // 3.4 跨停车场/租户校验：消息中的 tenantId/parkingLotId 必须与可信记录一致
        if (payload.getTenantId() != null
                && !payload.getTenantId().equals(parkingLot.getTenantId())) {
            result.fail(String.format("租户不匹配: 消息声称=%d, 可信=%d",
                    payload.getTenantId(), parkingLot.getTenantId()));
            return result;
        }

        if (payload.getParkingLotId() != null
                && !payload.getParkingLotId().equals(parkingLot.getId())) {
            result.fail(String.format("停车场不匹配: 消息声称=%d, 可信=%d",
                    payload.getParkingLotId(), parkingLot.getId()));
            return result;
        }

        // 3.5 车道方向校验
        if (device.getLaneId() != null) {
            ParkingLane lane = laneMapper.selectByIdIgnoreTenant(device.getLaneId());
            if (lane == null) {
                result.fail("车道不存在: laneId=" + device.getLaneId());
                return result;
            }
            if (lane.getStatus() == null || lane.getStatus() != 1) {
                result.fail("车道已停用: " + lane.getName());
                return result;
            }
            String direction = payload.getDirection();
            if (direction != null && lane.getType() != null) {
                // type: 1=ENTRY, 2=EXIT, 3=MIXED
                if (lane.getType() != 3 && !direction.equals(
                        lane.getType() == 1 ? "ENTRY" : "EXIT")) {
                    result.fail(String.format("方向不匹配: 事件方向=%s, 车道方向=%d (lane=%s)",
                            direction, lane.getType(), lane.getName()));
                    return result;
                }
            }

            // 3.5b 双向车道：相机有识别方向时，覆盖事件方向
            //      确保入场/出场路由基于相机实际安装位置，而非事件发布端填写值
            if (lane.getType() != null && lane.getType() == 3
                    && device.getRecognitionDirection() != null) {
                String cameraDirection = device.getRecognitionDirection() == 1 ? "ENTRY" : "EXIT";
                if (!cameraDirection.equals(payload.getDirection())) {
                    log.info("双向车道方向由相机推导覆盖: lane={} device={} cameraDirection={} originalDirection={}",
                            lane.getName(), device.getId(), cameraDirection, payload.getDirection());
                    payload.setDirection(cameraDirection);
                }
            }
        }

        // 3.5c 识别失败接管：车牌为空时，保存上下文并返回
        if (plateIsEmpty) {
            result.recognitionFailed = true;
            result.parkingLotId = parkingLot.getId();
            result.tenantId = parkingLot.getTenantId();
            result.laneId = device.getLaneId();
            result.deviceId = device.getId();
            result.eventTime = payload.getEventTime() != null
                    ? payload.getEventTime() : LocalDateTime.now();
            return result;
        }

        // 3.6 标记相机来源（仅当设备绑定了车道且有识别方向）
        if (device.getLaneId() != null && device.getRecognitionDirection() != null) {
            String source = cameraFailoverService.getActiveSource(
                    device.getLaneId(), device.getRecognitionDirection());
            payload.setCameraSource(source);
        }

        // 3.7 基本格式校验（非阻塞性，仅记录）
        if (!PlateStandardizer.isValidFormat(result.standardizedPlate)) {
            log.info("车牌格式可能异常（非阻塞）: plate={} eventId={}",
                    result.standardizedPlate, payload.getEventId());
        }

        result.success = true;
        return result;
    }

    // ==================== 数据库更新 ====================

    /**
     * 更新事件日志的处理结果。
     * <p>
     * 直接 UPDATE 已存在的记录（由 T28 Publisher 插入）。
     */
    private void updateEventLog(RecognitionEventPayload payload, ProcessingResult result) {
        RecognitionEventLog update = new RecognitionEventLog();
        update.setStandardizedPlate(result.standardizedPlate);
        if (result.recognitionFailed) {
            update.setStatus("RECOGNITION_FAILED");
            update.setTempPlateFlag(1);
        } else if (result.success) {
            update.setStatus("PROCESSED");
            update.setTempPlateFlag(0);
        } else {
            update.setStatus("FAILED");
            update.setFailureReason(result.failureReason);
        }
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            update.setCameraSource(payload.getCameraSource());
        }

        int rows = eventLogMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));

        if (rows == 0) {
            log.warn("事件日志不存在，无法更新状态（可能 DB 事务未提交）: eventId={}", payload.getEventId());
        }
    }

    /**
     * 更新事件日志为失败状态（用于 BusinessException 捕获后）。
     */
    private void updateEventLogFailed(RecognitionEventPayload payload, String reason) {
        RecognitionEventLog update = new RecognitionEventLog();
        update.setStatus("FAILED");
        update.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);

        if (payload.getPlateNumber() != null) {
            String standardized = PlateStandardizer.normalize(payload.getPlateNumber());
            if (standardized != null) {
                update.setStandardizedPlate(standardized);
            }
        }
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            update.setCameraSource(payload.getCameraSource());
        }

        int rows = eventLogMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));

        if (rows == 0) {
            log.warn("事件日志不存在，无法更新失败状态: eventId={}", payload.getEventId());
        }
    }

    // ==================== 识别失败处理（任务包 3-4） ====================

    /**
     * 识别失败接管入口。
     */
    private void handleRecognitionFailure(RecognitionEventPayload payload, ProcessingResult result) {
        if (!"ENTRY".equals(payload.getDirection())) {
            result.fail("出场方向识别失败（无牌车出场由岗亭手动匹配）");
            return;
        }

        String strategy = paramResolver.getString(
                ParamKeys.RECOGNITION_FAIL_STRATEGY, result.parkingLotId);

        if (ParamKeys.RECOGNITION_AUTO_RELEASE.equals(strategy)) {
            handleAutoRelease(payload, result);
        } else {
            handleManualAlert(payload, result);
        }
    }

    /**
     * MANUAL 策略：推送 WebSocket 告警 + 创建 MonitorAlert 记录。
     */
    private void handleManualAlert(RecognitionEventPayload payload, ProcessingResult result) {
        log.info("无牌车识别失败-MANUAL策略: eventId={} lotId={} laneId={}",
                payload.getEventId(), result.parkingLotId, result.laneId);

        // 1. 推送 WebSocket 告警到岗亭端
        boothWebSocketPublisher.sendRecognitionFailedAlert(
                result.parkingLotId,
                payload.getEventId(),
                payload.getLogId(),
                result.parkingLotId,
                result.laneId,
                payload.getDirection(),
                payload.getImagePath(),
                result.eventTime);

        // 2. 创建 MonitorAlert 记录
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(result.parkingLotId);
        RecognitionEventLog eventLog = eventLogMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));
        if (lot != null && eventLog != null && monitorAlertService != null) {
            eventLog.setPlateNumber("无牌车");
            eventLog.setFailureReason("识别失败，等待岗亭处理");
            try {
                monitorAlertService.createRecognitionFailAlert(lot, eventLog);
            } catch (Exception e) {
                log.warn("创建识别失败告警 MonitorAlert 失败: eventId={} error={}",
                        payload.getEventId(), e.getMessage());
            }
        }
    }

    /**
     * AUTO_RELEASE 策略：自动生成临时车牌 + 创建在场记录 + 开闸放行。
     */
    private void handleAutoRelease(RecognitionEventPayload payload, ProcessingResult result) {
        log.info("无牌车识别失败-AUTO_RELEASE策略: eventId={} lotId={} laneId={}",
                payload.getEventId(), result.parkingLotId, result.laneId);

        String tempPlate;
        try {
            tempPlate = tempPlateNumberGenerator.generate(result.parkingLotId);
        } catch (BusinessException e) {
            log.warn("临时车牌号生成失败-AUTO_RELEASE降级为MANUAL: eventId={} reason={}",
                    payload.getEventId(), e.getMessage());
            handleManualAlert(payload, result);
            return;
        }

        ParkingRecord record = new ParkingRecord();
        record.setTenantId(result.tenantId);
        record.setParkingLotId(result.parkingLotId);
        record.setLaneId(result.laneId);
        record.setDeviceId(result.deviceId);
        record.setStandardizedPlate(tempPlate);
        record.setTempPlateFlag(1);
        record.setStatus(ParkingRecord.STATUS_PARKING);
        record.setEntryTime(result.eventTime != null ? result.eventTime : LocalDateTime.now());
        record.setEntryImagePath(payload.getImagePath());
        recordMapper.insert(record);

        result.standardizedPlate = tempPlate;

        parkingLotMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles + 1")
                        .setSql("remaining_spaces = remaining_spaces - 1")
                        .eq(ParkingLot::getId, result.parkingLotId));

        try {
            deviceService.openGateByLane(result.laneId,
                    "无牌车自动放行(" + tempPlate + ")");
        } catch (Exception e) {
            log.warn("无牌车自动开闸失败（不影响记录创建）: plate={} laneId={} error={}",
                    tempPlate, result.laneId, e.getMessage());
        }

        log.info("无牌车自动放行完成: tempPlate={} recordId={} lotId={}",
                tempPlate, record.getId(), result.parkingLotId);
    }

    // ==================== 内部类 ====================

    /**
     * 校验和标准化结果。
     */
    private static class ProcessingResult {
        boolean success = false;
        boolean recognitionFailed = false;
        String standardizedPlate;
        String failureReason;
        Long parkingLotId;
        Long tenantId;
        Long laneId;
        Long deviceId;
        LocalDateTime eventTime;

        void fail(String reason) {
            this.success = false;
            this.failureReason = reason;
        }
    }
}
