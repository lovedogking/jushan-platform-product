package com.jushan.platform.modules.device.webhook;

import com.jushan.common.R;
import com.jushan.platform.modules.device.dto.DeviceWebhookEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Device Access Webhook 接收控制器。
 * <p>
 * 接收 Device Access v0.4 通过 HTTP Webhook 推送的车牌识别事件。
 * <b>不鉴权</b>（Device Access 作为客户端调用，无用户 Token），
 * 通过 IP 白名单或请求签名验证来源（一期实现 IP 白名单）。
 * <p>
 * 所有业务处理异常内部消化，始终返回 HTTP 200，
 * 避免 Device Access 因非 200 响应而重试。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device-webhook")
public class DeviceWebhookController {

    private final DeviceWebhookService deviceWebhookService;

    /**
     * IP 白名单（逗号分隔）。
     * <p>
     * 配置键：{@code device-access.webhook.allowed-ips}
     * 特殊值 {@code *} 表示放行所有 IP（仅限开发环境）。
     */
    @Value("${device-access.webhook.allowed-ips:*}")
    private String allowedIps;

    public DeviceWebhookController(DeviceWebhookService deviceWebhookService) {
        this.deviceWebhookService = deviceWebhookService;
    }

    /**
     * 接收 Device Access Webhook 事件。
     * <p>
     * 接受 Device Event 信封格式（payload 嵌套字段），
     * 适配转换到平台 DeviceWebhookEvent 后委托业务处理。
     *
     * @param body    Webhook 推送原始事件体（Map 接收兼容嵌套信封格式）
     * @param request HTTP 请求（用于获取客户端 IP）
     * @return R.ok()
     */
    @PostMapping("/events")
    public R<Void> receiveEvent(@RequestBody Map<String, Object> body,
                                 HttpServletRequest request) {
        String clientIp = getClientIp(request);

        // 提取基本信息
        String eventId = stringValue(body, "eventId");
        String eventType = stringValue(body, "eventType");
        String deviceSn = stringValue(body, "deviceSn");

        // IP 白名单校验
        if (!isAllowedIp(clientIp)) {
            log.warn("Webhook 请求 IP 不在白名单中，已拒绝: clientIp={}, eventId={}",
                    clientIp, eventId);
            return R.ok();
        }

        log.info("收到 Device Access Webhook 事件: eventId={}, eventType={}, deviceSn={}, clientIp={}",
                eventId, eventType, deviceSn, clientIp);

        // 从嵌套信封中提取 payload，适配到平台 DTO
        DeviceWebhookEvent event = mapToEvent(body);

        // 业务处理异常内部消化，确保始终返回 200
        try {
            deviceWebhookService.processEvent(event);
        } catch (Exception e) {
            log.error("Webhook 事件处理异常: eventId={}, deviceSn={}, error={}",
                    eventId, deviceSn, e.getMessage(), e);
        }

        return R.ok();
    }

    /**
     * 将 Device Access 嵌套信封格式适配到平台 DeviceWebhookEvent。
     * <p>
     * DA v0.4 推送格式：
     * <pre>
     * {
     *   "eventId": "...",
     *   "eventType": "PLATE_RECOGNIZED",
     *   "deviceSn": "...",
     *   "payload": {
     *     "plateNo": "桂A88888",
     *     "confidence": 100,
     *     "direction": 4,
     *     ...
     *   }
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private DeviceWebhookEvent mapToEvent(Map<String, Object> body) {
        DeviceWebhookEvent event = new DeviceWebhookEvent();
        event.setEventId(stringValue(body, "eventId"));
        event.setEventType(stringValue(body, "eventType"));
        event.setDeviceSn(stringValue(body, "deviceSn"));

        // 提取嵌套 payload
        Map<String, Object> payload = null;
        Object payloadObj = body.get("payload");
        if (payloadObj instanceof Map) {
            payload = (Map<String, Object>) payloadObj;
        }

        if (payload != null) {
            // 字段映射：DA 的 plateNo → 平台的 plateNumber
            String plateNo = stringValue(payload, "plateNo");
            if (plateNo != null) {
                event.setPlateNumber(plateNo);
            }
            event.setPlateColor(stringValue(payload, "plateColor"));

            // 置信度转换：DA 可能传 0~100 整数，平台期望 0.0~1.0 Double
            Object confObj = payload.get("confidence");
            if (confObj instanceof Number) {
                double conf = ((Number) confObj).doubleValue();
                if (conf > 1.0) {
                    conf = conf / 100.0;
                }
                event.setConfidence(conf);
            }

            // 方向：DA 传数字方向（臻识协议），平台需要 ENTRY/EXIT
            Object dirObj = payload.get("direction");
            if (dirObj instanceof Number) {
                event.setDirection(mapDirection(((Number) dirObj).intValue()));
            } else if (dirObj instanceof String) {
                event.setDirection((String) dirObj);
            }

            // 抓拍时间
            String occurredAt = stringValue(body, "occurredAt");
            if (occurredAt != null) {
                event.setCaptureTime(occurredAt);
            }

            // 图片 URL
            event.setImageUrl(stringValue(payload, "imagePath"));
            event.setPlateImageUrl(stringValue(payload, "plateImagePath"));
        }

        return event;
    }

    /**
     * 臻识方向数值 → 平台方向枚举。
     * <p>
     * 臻识协议方向值：
     * <ul>
     *   <li>0 = 未知</li>
     *   <li>1 = 入口</li>
     *   <li>2 = 出口</li>
     *   <li>4 = 未知/其他</li>
     * </ul>
     */
    private String mapDirection(int direction) {
        return switch (direction) {
            case 1 -> "ENTRY";
            case 2 -> "EXIT";
            default -> null;
        };
    }

    /**
     * 从 Map 中安全提取 String 值。
     */
    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 判断客户端 IP 是否在白名单中。
     */
    private boolean isAllowedIp(String clientIp) {
        if ("*".equals(allowedIps)) {
            return true;
        }
        if (allowedIps == null || allowedIps.isBlank()) {
            log.warn("Webhook IP 白名单未配置，拒绝所有请求");
            return false;
        }
        List<String> allowedList = parseAllowedIps();
        if (allowedList.isEmpty()) {
            return false;
        }
        for (String allowed : allowedList) {
            if (clientIp.equals(allowed) || clientIp.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseAllowedIps() {
        return Collections.unmodifiableList(
                Arrays.stream(allowedIps.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
