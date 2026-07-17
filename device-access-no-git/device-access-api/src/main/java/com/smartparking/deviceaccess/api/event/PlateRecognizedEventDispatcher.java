package com.smartparking.deviceaccess.api.event;

import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.event.PlateRecognizedData;
import com.smartparking.deviceaccess.common.event.PlateRecognizedListener;
import com.smartparking.deviceaccess.event.DeviceEvent;
import com.smartparking.deviceaccess.event.EventPublisher;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 车牌识别事件分发器。
 * <p>
 * 实现 common 模块的 {@link PlateRecognizedListener} 接口，
 * 被 adapter 模块的各品牌 MessageHandler 回调。
 * <p>
 * 职责：
 * <ol>
 *   <li>接收 adapter 回调的车牌识别数据（品牌无关）</li>
 *   <li>查 DeviceRegistry 填充业务字段（tenantId/parkingLotId/laneId/platformDeviceId）</li>
 *   <li>查 DeviceProductRegistry 获取厂商信息（vendor/brand）</li>
 *   <li>构造统一事件信封 {@link DeviceEvent}</li>
 *   <li>调用 {@link EventPublisher} 异步推送 Webhook</li>
 * </ol>
 * <p>
 * 异常仅日志告警，不影响 adapter 的 MQTT 消息处理主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlateRecognizedEventDispatcher implements PlateRecognizedListener {

    private final DeviceRegistry deviceRegistry;
    private final DeviceProductRegistry productRegistry;
    private final EventPublisher eventPublisher;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    @Override
    public void onPlateRecognized(PlateRecognizedData data) {
        try {
            // 1. 查 DeviceRegistry 获取设备业务字段
            Device device = deviceRegistry.findByDeviceId(data.deviceSn());
            if (device == null) {
                log.warn("Plate recognized but device not registered: sn={}, license={}",
                        data.deviceSn(), data.license());
                return;
            }

            // 2. 查 DeviceProductRegistry 获取厂商信息
            DeviceProduct product = null;
            if (device.getProductId() != null) {
                product = productRegistry.getById(device.getProductId());
            }
            String vendor = product != null ? product.getBrand() : "UNKNOWN";

            // 3. 构造 payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("plateNo", data.license());
            if (data.plateColor() != null) {
                payload.put("plateColor", data.plateColor());
            }
            if (data.confidence() != null) {
                payload.put("confidence", data.confidence());
            }
            if (data.direction() != null) {
                payload.put("direction", data.direction());
            }
            if (data.imagePath() != null) {
                payload.put("imagePath", data.imagePath());
            }

            // 4. 构造事件信封
            String occurredAt = formatIso(data.occurredAtMillis());
            String receivedAt = ISO_FORMATTER.format(Instant.now());

            DeviceEvent event = DeviceEvent.builder()
                    .eventId("evt_" + UUID.randomUUID())
                    .eventType("PLATE_RECOGNIZED")
                    .tenantId(device.getTenantId())
                    .parkingLotId(device.getParkingLotId())
                    .laneId(device.getLaneId())
                    .platformDeviceId(device.getPlatformDeviceId())
                    .deviceSn(data.deviceSn())
                    .vendor(vendor)
                    .occurredAt(occurredAt)
                    .receivedAt(receivedAt)
                    .payload(payload)
                    .build();

            // 5. 异步推送
            eventPublisher.publish(event);

            log.info("Plate recognized event dispatched: sn={}, license={}, eventId={}",
                    data.deviceSn(), data.license(), event.getEventId());

        } catch (Exception e) {
            log.error("Failed to dispatch plate recognized event: sn={}, license={}",
                    data.deviceSn(), data.license(), e);
        }
    }

    /**
     * 将毫秒时间戳格式化为 ISO-8601 UTC 字符串。
     */
    private String formatIso(Long millis) {
        if (millis == null) {
            return null;
        }
        return ISO_FORMATTER.format(Instant.ofEpochMilli(millis));
    }
}
