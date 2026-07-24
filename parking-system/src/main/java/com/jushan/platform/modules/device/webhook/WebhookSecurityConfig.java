package com.jushan.platform.modules.device.webhook;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Webhook 安全过滤器配置。
 * <p>
 * 注册 {@link WebhookVerificationFilter} 拦截
 * {@code /api/v1/device-webhook/*} 并执行 HMAC-SHA256 签名校验。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Configuration
public class WebhookSecurityConfig {

    @Value("${device-access.webhook.signature-enabled:true}")
    private boolean signatureEnabled;

    @Bean
    public FilterRegistrationBean<WebhookVerificationFilter> webhookVerificationFilterRegistration(
            com.jushan.platform.modules.device.mapper.DeviceMapper deviceMapper,
            com.jushan.platform.modules.device.mapper.WebhookSecretMapper webhookSecretMapper,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {

        FilterRegistrationBean<WebhookVerificationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new WebhookVerificationFilter(
                deviceMapper, webhookSecretMapper, redisTemplateProvider, signatureEnabled));
        registration.addUrlPatterns("/api/v1/device-webhook/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.setName("webhookVerificationFilter");
        return registration;
    }
}
