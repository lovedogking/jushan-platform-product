package com.smartparking.deviceaccess.api.event;

import com.smartparking.deviceaccess.api.image.ImageStorageService;
import com.smartparking.deviceaccess.api.image.RemoteImageDownloader;
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
    private final ImageStorageService imageStorageService;
    private final RemoteImageDownloader remoteImageDownloader;

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

            // 2b. 图片 URL 化：芊熠相机「独立上传图片」（协议 §7.1.2）由相机 HTTP POST 到本平台，
            //     result 上报的 full_pic_path 是相机本地路径，无法直接访问；
            //     此处按关联键（sn + utc_ts）构造可访问 URL（nginx 反代到图片存储目录）。
            //     臻识等品牌事件携带的是远程限时 URL（如 OSS 签名地址，约 1 小时过期），
            //     立即下载转存本地并替换为本地 URL；失败则保留原地址（限时内仍可用）。
            String imagePath = data.imagePath();
            String plateImagePath = data.plateImagePath();
            if (isQianyi(vendor) && data.occurredAtMillis() != null) {
                long epochSeconds = data.occurredAtMillis() / 1000;
                imagePath = imageStorageService.buildFullImageUrl(data.deviceSn(), epochSeconds);
                plateImagePath = imageStorageService.buildPlateImageUrl(data.deviceSn(), epochSeconds);
            } else {
                long epochSeconds = data.occurredAtMillis() != null
                        ? data.occurredAtMillis() / 1000 : Instant.now().getEpochSecond();
                if (imagePath != null) {
                    String local = remoteImageDownloader.downloadAndStore(
                            data.deviceSn(), epochSeconds, imagePath, false);
                    if (local != null) {
                        imagePath = local;
                    }
                }
                if (plateImagePath != null) {
                    String localPlate = remoteImageDownloader.downloadAndStore(
                            data.deviceSn(), epochSeconds, plateImagePath, true);
                    if (localPlate != null) {
                        plateImagePath = localPlate;
                    }
                }
            }

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
            if (imagePath != null) {
                payload.put("imagePath", imagePath);
            }
            if (plateImagePath != null) {
                payload.put("plateImagePath", plateImagePath);
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
     * 判断厂商是否芊熠（product.brand 可能为中文或英文标准值）。
     */
    private boolean isQianyi(String vendor) {
        return "芊熠".equals(vendor) || "QIANYI".equalsIgnoreCase(vendor);
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
