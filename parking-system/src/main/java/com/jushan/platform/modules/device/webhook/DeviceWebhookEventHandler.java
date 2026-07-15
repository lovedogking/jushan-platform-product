package com.jushan.platform.modules.device.webhook;

import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.service.RecognitionEventService;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Webhook 事件处理器——将已验证的 Device Access Webhook 事件适配到平台识别事件处理管线。
 * <p>
 * 职责：
 * <ul>
 *   <li>构造 {@link RecognitionEventCmd}（从已验证的可信数据填充）</li>
 *   <li>调用 {@link RecognitionEventService#handleEvent(RecognitionEventCmd)}</li>
 *   <li>记录处理结果日志</li>
 * </ul>
 * <p>
 * 本组件<b>不负责</b>幂等校验、设备身份验证和方向匹配——这些由 {@link DeviceWebhookService} 完成。
 * 本组件是未来扩展不同事件类型（如设备状态变更、故障告警）的扩展点。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class DeviceWebhookEventHandler {

    private final RecognitionEventService recognitionEventService;

    public DeviceWebhookEventHandler(RecognitionEventService recognitionEventService) {
        this.recognitionEventService = recognitionEventService;
    }

    /**
     * 处理已验证的车牌识别事件。
     * <p>
     * 调用方保证所有参数来自平台可信记录（非 Device Access 推送原文）。
     *
     * @param plateNumber  车牌号（已标准化为大写）
     * @param plateColor   车牌颜色
     * @param direction    识别方向（ENTRY / EXIT）
     * @param parkingLotId 可信停车场 ID（来自设备台账）
     * @param laneId       可信车道 ID（来自设备台账）
     * @param imageUrl     抓拍图片 URL
     * @param confidence   置信度
     * @param deviceSn     设备序列号（来自设备台账）
     * @param eventId      Device Access 事件 ID（用于全链路追踪）
     * @param captureTime  抓拍时间
     * @return 识别处理结果
     */
    public RecognitionResultVO handlePlateRecognized(String plateNumber, String plateColor,
                                                      String direction, Long parkingLotId,
                                                      Long laneId, String imageUrl,
                                                      Integer confidence, String deviceSn,
                                                      String eventId, String captureTime) {
        RecognitionEventCmd cmd = new RecognitionEventCmd();
        cmd.setPlateNumber(plateNumber);
        cmd.setPlateColor(plateColor);
        cmd.setDirection(direction);
        cmd.setParkingLotId(parkingLotId);
        cmd.setLaneId(laneId);
        cmd.setCaptureImage(imageUrl);
        cmd.setConfidence(confidence);
        cmd.setDeviceSn(deviceSn);
        cmd.setEventId(eventId);
        cmd.setCaptureTime(captureTime);

        log.info("Webhook 事件处理: eventId={}, deviceSn={}, plate={}, direction={}, lotId={}, laneId={}",
                eventId, deviceSn, plateNumber, direction, parkingLotId, laneId);

        RecognitionResultVO result = recognitionEventService.handleEvent(cmd);

        log.info("Webhook 事件处理完成: eventId={}, plate={}, allowPass={}, exception={}",
                eventId, plateNumber, result.getAllowPass(), result.getException());

        return result;
    }
}
