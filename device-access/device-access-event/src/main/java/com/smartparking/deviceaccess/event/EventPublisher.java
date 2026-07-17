package com.smartparking.deviceaccess.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 事件推送器 — 通过 HTTP POST Webhook 异步推送事件到 Parking Platform。
 * <p>
 * 实现约束：
 * <ul>
 *   <li>Webhook URL 未配置时静默跳过</li>
 *   <li>推送失败仅日志告警，不抛异常（不影响 adapter 主流程）</li>
 *   <li>内存重试 retryCount 次，固定间隔 retryIntervalSeconds 秒</li>
 *   <li>内存重试耗尽后写入 t_event_outbox，由 EventRetryService 定时重试</li>
 *   <li>配置 secretKey 后发送 X-Signature 头（HMAC-SHA256(eventId + timestamp)）</li>
 *   <li>不硬编码业务 URL 或响应格式</li>
 * </ul>
 *
 * @since v0.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final WebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final EventOutboxRepository outboxRepository;

    /**
     * 异步推送事件到 Webhook。
     * <p>
     * 使用 @Async 确保不阻塞 MQTT 消息处理线程。
     *
     * @param event 设备事件信封
     */
    @Async
    public void publish(DeviceEvent event) {
        if (!properties.isEnabled()) {
            log.debug("Webhook disabled, skip event: eventId={}, type={}",
                    event.getEventId(), event.getEventType());
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize event: eventId={}", event.getEventId(), e);
            return;
        }

        boolean pushed = doPush(event.getEventId(), event.getEventType(), json);
        if (!pushed) {
            try {
                outboxRepository.savePending(event, json);
                log.info("Event saved to outbox: eventId={}, type={}",
                        event.getEventId(), event.getEventType());
            } catch (Exception ex) {
                log.error("Failed to save event to outbox: eventId={}", event.getEventId(), ex);
            }
        }
    }

    /**
     * 同步执行 HTTP 推送（供 EventPublisher 和 EventRetryService 复用）。
     *
     * @param eventId   事件 ID
     * @param eventType 事件类型
     * @param json      JSON 报文
     * @return true 表示推送成功
     */
    boolean doPush(String eventId, String eventType, String json) {
        int maxAttempts = properties.getRetryCount() + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = buildRequest(eventId, json);

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.info("Webhook push success: eventId={}, type={}, httpStatus={}",
                            eventId, eventType, statusCode);
                    return true;
                }

                log.warn("Webhook push failed (attempt {}/{}): eventId={}, httpStatus={}, body={}",
                        attempt, maxAttempts, eventId,
                        statusCode, response.body());
            } catch (Exception e) {
                log.warn("Webhook push exception (attempt {}/{}): eventId={}, error={}",
                        attempt, maxAttempts, eventId, e.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(properties.getRetryIntervalSeconds() * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Webhook retry interrupted: eventId={}", eventId);
                    return false;
                }
            }
        }

        log.error("Webhook push exhausted all retries: eventId={}, type={}, url={}",
                eventId, eventType, properties.getUrl());
        return false;
    }

    private HttpRequest buildRequest(String eventId, String json) {
        long timestamp = Instant.now().getEpochSecond();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getUrl()))
                .header("Content-Type", "application/json")
                .header("X-Timestamp", String.valueOf(timestamp))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (StringUtils.hasText(properties.getSecretKey())) {
            String signature = computeSignature(eventId, timestamp);
            if (signature != null) {
                builder.header("X-Signature", signature);
            }
        }

        return builder.build();
    }

    private String computeSignature(String eventId, long timestamp) {
        try {
            String data = eventId + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Failed to compute HMAC signature: {}", e.getMessage());
            return null;
        }
    }
}
