package com.jushan.system.ws;

import com.jushan.system.dto.RemoteGateAlertDTO;
import com.jushan.system.entity.MonitorAlert;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.vo.DeviceStatusVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 岗亭监控 WebSocket 推送器（P005）。
 * <p>
 * 负责将识别事件、车位变化、设备状态变化、异常提醒推送到岗亭前端。
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>所有推送方法内部捕获异常，确保推送失败不阻断主业务事务</li>
 *   <li>按停车场维度广播，topic 路径为 {@code /topic/booth/{parkingLotId}/...}</li>
 *   <li>parkingLotId 由调用方从可信记录推导，禁止信任外部传入</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class BoothWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(BoothWebSocketPublisher.class);

    /** 识别事件 topic 模板。 */
    private static final String TOPIC_EVENTS = "/topic/booth/%d/events";

    /** 车位变化 topic 模板。 */
    private static final String TOPIC_SPACES = "/topic/booth/%d/spaces";

    /** 设备状态 topic 模板。 */
    private static final String TOPIC_DEVICE_STATUS = "/topic/booth/%d/device-status";

    /** 异常提醒 topic 模板。 */
    private static final String TOPIC_ALERTS = "/topic/booth/%d/alerts";

    /** 远程开闸通知 topic 模板。 */
    private static final String TOPIC_REMOTE_GATE = "/topic/booth/%d/remote-gate-alert";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SimpMessagingTemplate messagingTemplate;

    public BoothWebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 推送识别事件。
     *
     * @param parkingLotId 停车场 ID（可信）
     * @param event        识别事件日志
     */
    public void sendRecognitionEvent(Long parkingLotId, RecognitionEventLog event) {
        if (parkingLotId == null || event == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", event.getEventId());
            payload.put("logId", event.getId());
            payload.put("plateNumber", event.getPlateNumber());
            payload.put("standardizedPlate", event.getStandardizedPlate());
            payload.put("direction", event.getDirection());
            payload.put("eventTime", format(event.getEventTime()));
            payload.put("confidence", event.getConfidence());
            payload.put("source", event.getSource());
            payload.put("status", event.getStatus());
            payload.put("laneId", event.getLaneId());
            payload.put("deviceId", event.getDeviceId());
            payload.put("imagePath", event.getImagePath());
            payload.put("plateImagePath", event.getPlateImagePath());

            send(String.format(TOPIC_EVENTS, parkingLotId), payload);
            log.debug("识别事件已推送到岗亭: parkingLotId={}, eventId={}", parkingLotId, event.getEventId());
        } catch (Exception e) {
            log.warn("识别事件 WebSocket 推送失败（不影响主业务）: parkingLotId={}, eventId={}, error={}",
                    parkingLotId, event.getEventId(), e.getMessage());
        }
    }

    /**
     * 推送车位变化。
     *
     * @param parkingLotId     停车场 ID（可信）
     * @param remainingSpaces  剩余车位
     * @param currentVehicles  在场车辆数
     * @param totalSpaces      总车位数
     */
    public void sendSpaceUpdate(Long parkingLotId, Integer remainingSpaces,
                                 Integer currentVehicles, Integer totalSpaces) {
        if (parkingLotId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("remainingSpaces", remainingSpaces);
            payload.put("currentVehicles", currentVehicles);
            payload.put("totalSpaces", totalSpaces);
            payload.put("updatedAt", format(java.time.LocalDateTime.now()));

            send(String.format(TOPIC_SPACES, parkingLotId), payload);
            log.debug("车位变化已推送到岗亭: parkingLotId={}, remaining={}", parkingLotId, remainingSpaces);
        } catch (Exception e) {
            log.warn("车位变化 WebSocket 推送失败（不影响主业务）: parkingLotId={}, error={}",
                    parkingLotId, e.getMessage());
        }
    }

    /**
     * 推送设备状态变化。
     *
     * @param parkingLotId 停车场 ID（可信）
     * @param vo           设备状态视图
     */
    public void sendDeviceStatus(Long parkingLotId, DeviceStatusVO vo) {
        if (parkingLotId == null || vo == null) {
            return;
        }
        try {
            send(String.format(TOPIC_DEVICE_STATUS, parkingLotId), vo);
            log.debug("设备状态已推送到岗亭: parkingLotId={}, deviceId={}", parkingLotId, vo.getDeviceId());
        } catch (Exception e) {
            log.warn("设备状态 WebSocket 推送失败（不影响主业务）: parkingLotId={}, deviceId={}, error={}",
                    parkingLotId, vo.getDeviceId(), e.getMessage());
        }
    }

    /**
     * 推送异常提醒。
     *
     * @param parkingLotId 停车场 ID（可信）
     * @param alert        异常提醒
     */
    public void sendAlert(Long parkingLotId, MonitorAlert alert) {
        if (parkingLotId == null || alert == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", alert.getId());
            payload.put("alertType", alert.getAlertType());
            payload.put("severity", alert.getSeverity());
            payload.put("sourceId", alert.getSourceId());
            payload.put("message", alert.getMessage());
            payload.put("createdAt", format(alert.getCreatedAt()));

            send(String.format(TOPIC_ALERTS, parkingLotId), payload);
            log.debug("异常提醒已推送到岗亭: parkingLotId={}, alertId={}", parkingLotId, alert.getId());
        } catch (Exception e) {
            log.warn("异常提醒 WebSocket 推送失败（不影响主业务）: parkingLotId={}, alertId={}, error={}",
                    parkingLotId, alert.getId(), e.getMessage());
        }
    }

    /**
     * 推送支付完成通知到岗亭。
     * <p>
     * 小程序用户完成支付后调用，通知岗亭端该车牌已缴费可放行。
     *
     * @param parkingLotId 停车场 ID（可信）
     * @param orderId      订单 ID
     * @param orderNo      订单号
     * @param plateNumber  车牌号
     * @param amountCents  支付金额（分）
     */
    public void sendPaymentCompleted(Long parkingLotId, Long orderId, String orderNo,
                                      String plateNumber, int amountCents) {
        if (parkingLotId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "PAYMENT_COMPLETED");
            payload.put("orderId", orderId);
            payload.put("orderNo", orderNo);
            payload.put("plateNumber", plateNumber);
            payload.put("amountCents", amountCents);
            payload.put("amountYuan", String.format("%.2f", amountCents / 100.0));
            payload.put("paidAt", format(java.time.LocalDateTime.now()));

            send(String.format(TOPIC_EVENTS, parkingLotId), payload);
            log.info("支付完成通知已推送到岗亭: parkingLotId={}, orderId={}, plate={}",
                    parkingLotId, orderId, plateNumber);
        } catch (Exception e) {
            log.warn("支付完成通知 WebSocket 推送失败（不影响主业务）: parkingLotId={}, orderId={}, error={}",
                    parkingLotId, orderId, e.getMessage());
        }
    }

    /**
     * 推送远程开闸通知到岗亭（Phase 1 B2）。
     * <p>
     * 运营端远程开闸后调用，通知岗亭端弹窗显示操作信息。
     *
     * @param parkingLotId 停车场 ID（可信）
     * @param dto          远程开闸通知 DTO
     */
    public void sendRemoteGateAlert(Long parkingLotId, RemoteGateAlertDTO dto) {
        if (parkingLotId == null || dto == null) {
            return;
        }
        try {
            send(String.format(TOPIC_REMOTE_GATE, parkingLotId), dto);
            log.info("远程开闸通知已推送到岗亭: parkingLotId={}, operator={}, lane={}",
                    parkingLotId, dto.getOperatorName(), dto.getLaneName());
        } catch (Exception e) {
            log.warn("远程开闸 WebSocket 推送失败（不影响主业务）: parkingLotId={}, error={}",
                    parkingLotId, e.getMessage());
        }
    }

    /**
     * 推送识别失败告警（无牌车处理）到岗亭端（任务包 3-4）。
     *
     * @param parkingLotId 停车场 ID（可信）
     * @param eventId      识别事件 UUID
     * @param logId        事件日志自增 ID
     * @param lotId        停车场 ID（冗余便于前端使用）
     * @param laneId       车道 ID
     * @param direction    方向（ENTRY/EXIT）
     * @param imagePath    全景图路径
     * @param eventTime    事件时间
     */
    public void sendRecognitionFailedAlert(Long parkingLotId, String eventId,
            Long logId, Long lotId, Long laneId, String direction,
            String imagePath, java.time.LocalDateTime eventTime) {
        if (parkingLotId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "RECOGNITION_FAILED");
            payload.put("eventId", eventId);
            payload.put("logId", logId);
            payload.put("parkingLotId", lotId);
            payload.put("laneId", laneId);
            payload.put("direction", direction);
            payload.put("imagePath", imagePath);
            payload.put("eventTime", format(eventTime));
            payload.put("message", "入口识别失败，请手动处理无牌车辆");

            send(String.format(TOPIC_ALERTS, parkingLotId), payload);
            log.debug("识别失败告警已推送到岗亭: lotId={}, eventId={}", parkingLotId, eventId);
        } catch (Exception e) {
            log.warn("识别失败告警 WebSocket 推送失败（不影响主业务）: lotId={}, error={}",
                    parkingLotId, e.getMessage());
        }
    }

    /**
     * 底层发送方法。
     */
    private void send(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    private String format(java.time.LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
