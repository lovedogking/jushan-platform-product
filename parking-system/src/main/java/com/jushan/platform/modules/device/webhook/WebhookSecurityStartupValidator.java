package com.jushan.platform.modules.device.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Webhook 安全配置启动校验器。
 * <p>
 * 启动完成后检查 IP 白名单和签名校验配置是否符合当前环境安全要求。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Component
public class WebhookSecurityStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityStartupValidator.class);

    @Value("${device-access.webhook.allowed-ips:}")
    private String allowedIps;

    @Value("${device-access.webhook.signature-enabled:true}")
    private boolean signatureEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if ("*".equals(allowedIps)) {
            log.warn("=======================================================================");
            log.warn("!! Webhook IP 白名单为 *（全放行），生产环境存在安全风险！");
            log.warn("!! 请设置 DEVICE_ACCESS_WEBHOOK_ALLOWED_IPS 为 Device Access 服务 IP");
            log.warn("=======================================================================");
        }

        if (!signatureEnabled) {
            log.warn("=======================================================================");
            log.warn("!! Webhook HMAC 签名校验已关闭，生产环境禁止此配置！");
            log.warn("!! 请设置 DEVICE_ACCESS_WEBHOOK_SIGNATURE_ENABLED=true");
            log.warn("=======================================================================");
        }
    }
}
