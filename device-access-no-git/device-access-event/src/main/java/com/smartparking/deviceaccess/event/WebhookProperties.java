package com.smartparking.deviceaccess.event;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Webhook 推送配置。
 * <p>
 * 配置前缀：{@code device-access.webhook}
 * <p>
 * 当 url 为空或 enabled=false 时，EventPublisher 静默跳过推送。
 */
@Data
@ConfigurationProperties(prefix = "device-access.webhook")
public class WebhookProperties {

    /** Webhook 接收端 URL，为空时禁用推送 */
    private String url;

    /** HTTP 请求超时（秒） */
    private int timeoutSeconds = 5;

    /** 推送失败重试次数 */
    private int retryCount = 3;

    /** 重试间隔（秒） */
    private int retryIntervalSeconds = 5;

    /** HMAC-SHA256 签名密钥，为空时不发送 X-Signature */
    private String secretKey;

    /**
     * 判断 Webhook 是否启用。
     *
     * @return true 当 url 非空且非空白
     */
    public boolean isEnabled() {
        return url != null && !url.isBlank();
    }
}
