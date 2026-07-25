package com.smartparking.deviceaccess.api.event;

import com.smartparking.deviceaccess.api.image.ImageProperties;
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

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final ImageProperties imageProperties;

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

            // 2b. 图片 URL 化。芊熠 Q3 优先从 MQTT result 的 base64 字段（full_pic/plate_pic）
            //     解码落盘；若无 base64 则走 HTTP 独立上传等待（alonepush=1 模式，协议 §7.1.2）。
            //     臻识等品牌事件携带的是远程限时 URL（如 OSS 签名地址，约 1 小时过期），
            //     立即下载转存本地并替换为本地 URL；失败则保留原地址（限时内仍可用）。
            String imagePath = data.imagePath();
            String plateImagePath = data.plateImagePath();
            if (isQianyi(vendor)) {
                long epochSeconds = data.occurredAtMillis() != null
                        ? data.occurredAtMillis() / 1000 : Instant.now().getEpochSecond();
                // 优先 base64（MQTT 直传，alonepush=0 模式）
                if (imagePath != null && imagePath.startsWith("base64:")) {
                    imagePath = saveQianyiBase64Image(data.deviceSn(), epochSeconds,
                            imagePath.substring(7), false);
                } else {
                    imagePath = waitForLocalImage(data.deviceSn(), epochSeconds, false);
                }
                if (plateImagePath != null && plateImagePath.startsWith("base64:")) {
                    plateImagePath = saveQianyiBase64Image(data.deviceSn(), epochSeconds,
                            plateImagePath.substring(7), true);
                } else {
                    plateImagePath = waitForLocalImage(data.deviceSn(), epochSeconds, true);
                }
            } else {
                long epochSeconds = data.occurredAtMillis() != null
                        ? data.occurredAtMillis() / 1000 : Instant.now().getEpochSecond();

                // 臻识 FTP URL：相机本地文件标识被 ZhenshiMessageHandler 替换为 FTP HTTP URL，
                // 文件已在服务器本地磁盘（nginx 静态目录下），无需 HTTP 下载，直接在磁盘查找并转存。
                // URL 中的时间戳子秒部分可能与实际文件名有偏差，用通配匹配查找。
                if (imagePath != null && imagePath.startsWith(imageProperties.getPublicBaseUrl())) {
                    String local = resolveZhenshiFtpFile(imagePath, data.deviceSn(), epochSeconds, false);
                    if (local != null) imagePath = local;
                } else if (imagePath != null) {
                    String local = remoteImageDownloader.downloadAndStore(
                            data.deviceSn(), epochSeconds, imagePath, false);
                    if (local != null) imagePath = local;
                }
                if (plateImagePath != null && plateImagePath.startsWith(imageProperties.getPublicBaseUrl())) {
                    String localPlate = resolveZhenshiFtpFile(plateImagePath, data.deviceSn(), epochSeconds, true);
                    if (localPlate != null) plateImagePath = localPlate;
                } else if (plateImagePath != null) {
                    String localPlate = remoteImageDownloader.downloadAndStore(
                            data.deviceSn(), epochSeconds, plateImagePath, true);
                    if (localPlate != null) plateImagePath = localPlate;
                }
            }

            // 3. 构造 payload
            Map<String, Object> payload = new LinkedHashMap<>();

            // 臻识 fallback：full 目录通常不存在（相机仅上传 plate 图），
            // 若 imagePath 仍含 FTP 子目录标识(IVS)，说明解析失败，用 plateImagePath 顶替
            if (imagePath != null && imagePath.contains("/IVS(") &&
                    plateImagePath != null && !plateImagePath.contains("/IVS(")) {
                imagePath = plateImagePath;
                log.info("臻识 full 图不可用，用 plate 图顶替: {}", imagePath);
            }
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
     * 将芊熠 MQTT result 中的 base64 图片解码落盘并返回本地 URL。
     * <p>
     * Q3 相机在 alonepush=0（默认）模式下，result 消息的 full_pic / plate_pic
     * 字段携带 base64 编码图片。落盘后按标准路径构造可访问 URL。
     *
     * @param deviceSn     设备 SN
     * @param epochSeconds 识别时间（UTC 秒，与 MQTT utc_ts 一致）
     * @param base64Data   base64 编码图片数据
     * @param plateOnly    true=车牌特写图，false=全景图
     * @return 本地可访问 URL；解码失败返回 null
     */
    private String saveQianyiBase64Image(String deviceSn, long epochSeconds,
                                          String base64Data, boolean plateOnly) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64Data);
            String url;
            if (plateOnly) {
                imageStorageService.saveBytes(deviceSn, epochSeconds, null, bytes);
                url = imageStorageService.buildPlateImageUrl(deviceSn, epochSeconds);
            } else {
                imageStorageService.saveBytes(deviceSn, epochSeconds, bytes, null);
                url = imageStorageService.buildFullImageUrl(deviceSn, epochSeconds);
            }
            log.info("芊熠 base64 图片已落盘: sn={}, ts={}, plateOnly={}, url={}",
                    deviceSn, epochSeconds, plateOnly, url);
            return url;
        } catch (Exception e) {
            log.warn("芊熠 base64 图片解码落盘失败: sn={}, ts={}, plateOnly={}, error={}",
                    deviceSn, epochSeconds, plateOnly, e.getMessage());
            return null;
        }
    }

    /**
     * 等待芊熠/臻识相机 HTTP 上传的图片落盘（时序竞态：MQTT 事件早于 HTTP 上传完成），
     * 最多等待 3 秒，每次间隔 1 秒检查文件是否存在；超时返回 null。
     */
    private String waitForLocalImage(String deviceSn, long epochSeconds, boolean plateOnly) {
        for (int retry = 0; retry < 3; retry++) {
            if (retry > 0) {
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            }
            Path filePath = imageStorageService.resolveFilePath(deviceSn, epochSeconds, plateOnly);
            if (Files.exists(filePath)) {
                log.info("{} 图片已落盘(第{}次): {}", plateOnly ? "Plate" : "Full", retry + 1, filePath);
                return plateOnly
                        ? imageStorageService.buildPlateImageUrl(deviceSn, epochSeconds)
                        : imageStorageService.buildFullImageUrl(deviceSn, epochSeconds);
            }
        }
        log.info("{} 图片等待超时(3s): sn={}, ts={}", plateOnly ? "Plate" : "Full", deviceSn, epochSeconds);
        return null;
    }

    /**
     * 解析臻识 FTP 上传的图片文件：将 FTP HTTP URL 转为本地磁盘路径，
     * 通配匹配文件名（时间戳子秒部分可能偏移），找到后转存到标准图片目录。
     *
     * @param ftpUrl       FTP 图片的 HTTP URL（如 http://.../parking/IVS(ip)/.../0938150000_川A88888.jpg）
     * @param deviceSn     设备 SN
     * @param epochSeconds 事件时间（秒）
     * @param plateOnly    true=车牌特写，false=全景
     * @return 标准本地 URL；文件不存在或读取失败返回 null
     */
    private String resolveZhenshiFtpFile(String ftpUrl, String deviceSn, long epochSeconds, boolean plateOnly) {
        log.info("臻识 FTP 文件解析: url={}", ftpUrl);
        try {
            // 从 HTTP URL 反推文件系统路径：http://.../images/parking/... → {storageDir}/parking/...
            String publicBase = imageProperties.getPublicBaseUrl();
            if (!ftpUrl.startsWith(publicBase)) {
                return null;
            }
            String relativePath = ftpUrl.substring(publicBase.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            // 解析文件名和目录：parking/IVS(...)/channel_0/plate/2026-07-25/0938150000_川A88888.jpg
            int lastSlash = relativePath.lastIndexOf('/');
            if (lastSlash < 0) return null;
            String dir = relativePath.substring(0, lastSlash);
            String filename = relativePath.substring(lastSlash + 1);

            // 文件名格式：{HHmmss}{????}_{plate}.jpg，用通配替换子秒部分
            int underscoreIdx = filename.indexOf('_');
            if (underscoreIdx < 0) return null;
            // 时间部分去掉最后4位数字 + 保留*通配匹配
            String timePrefix = filename.substring(0, underscoreIdx);
            if (timePrefix.length() < 5) return null;
            // 去掉末尾4位子秒，用 * 替换
            String timeGlob = timePrefix.substring(0, timePrefix.length() - 4) + "*";
            String globPattern = timeGlob + filename.substring(underscoreIdx);

            Path dirPath = Paths.get(imageProperties.getStorageDir(), dir);
            if (!Files.isDirectory(dirPath)) {
                log.info("臻识 FTP 目录不存在(可能是full目录仅plate有图): {}", dirPath);
                return null;
            }

            log.info("臻识 FTP 磁盘查找: dir={}, pattern={}", dirPath, globPattern);
            final String regexPattern = globToRegex(globPattern);

            // MQTT 到达时 FTP 上传可能未完成（时序竞态），最多重试 3 次，每次间隔 1s
            for (int retry = 0; retry < 3; retry++) {
                if (retry > 0) {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
                }
                try (var stream = Files.list(dirPath)) {
                    var matches = stream
                            .filter(p -> p.getFileName().toString().matches(regexPattern))
                            .toList();
                    if (!matches.isEmpty()) {
                        Path match = matches.get(0);
                        log.info("臻识 FTP 匹配到文件(第{}次): {}", retry + 1, match);
                        byte[] bytes = Files.readAllBytes(match);
                        if (plateOnly) {
                            imageStorageService.saveBytes(deviceSn, epochSeconds, null, bytes);
                            return imageStorageService.buildPlateImageUrl(deviceSn, epochSeconds);
                        } else {
                            imageStorageService.saveBytes(deviceSn, epochSeconds, bytes, null);
                            return imageStorageService.buildFullImageUrl(deviceSn, epochSeconds);
                        }
                    }
                }
            }
            log.info("臻识 FTP 目录中未找到匹配文件: dir={}, pattern={}", dirPath, globPattern);
            return null;
        } catch (Exception e) {
            log.warn("解析臻识 FTP 文件失败: url={}", ftpUrl, e);
            return null;
        }
    }

    /**
     * 简单 glob → Java 正则转换（仅支持 * 通配符）。
     */
    private static String globToRegex(String glob) {
        return "^" + java.util.regex.Pattern.quote(glob).replace("*", "\\E.*\\Q") + "$";
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
