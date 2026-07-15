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
     * 内部完成幂等校验、设备身份验证、方向匹配、租户上下文推导，
     * 所有异常内部消化，始终返回 HTTP 200。
     *
     * @param event   Webhook 推送事件体
     * @param request HTTP 请求（用于获取客户端 IP）
     * @return R.ok()
     */
    @PostMapping("/events")
    public R<Void> receiveEvent(@RequestBody DeviceWebhookEvent event,
                                 HttpServletRequest request) {
        String clientIp = getClientIp(request);

        // IP 白名单校验
        if (!isAllowedIp(clientIp)) {
            log.warn("Webhook 请求 IP 不在白名单中，已拒绝: clientIp={}, eventId={}",
                    clientIp, event.getEventId());
            // 仍返回 200 避免泄露接口存在信息，但不处理事件
            return R.ok();
        }

        log.info("收到 Device Access Webhook 事件: eventId={}, eventType={}, deviceSn={}, plate={}, direction={}, clientIp={}",
                event.getEventId(), event.getEventType(), event.getDeviceSn(),
                event.getPlateNumber(), event.getDirection(), clientIp);

        // 业务处理异常内部消化，确保始终返回 200
        try {
            deviceWebhookService.processEvent(event);
        } catch (Exception e) {
            log.error("Webhook 事件处理异常: eventId={}, deviceSn={}, error={}",
                    event.getEventId(), event.getDeviceSn(), e.getMessage(), e);
        }

        return R.ok();
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
        // 支持精确匹配和 CIDR 前缀匹配（如 192.168.1.）
        for (String allowed : allowedList) {
            if (clientIp.equals(allowed) || clientIp.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 IP 白名单配置。
     */
    private List<String> parseAllowedIps() {
        return Collections.unmodifiableList(
                Arrays.stream(allowedIps.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()
        );
    }

    /**
     * 获取客户端真实 IP（考虑反向代理）。
     */
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
