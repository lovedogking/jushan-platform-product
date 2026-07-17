package com.smartparking.deviceaccess.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Event 模块配置。
 * <p>
 * 启用 WebhookProperties 配置绑定和异步推送支持。
 * 提供 HttpClient Bean 供 EventPublisher 使用。
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(WebhookProperties.class)
public class EventModuleConfig {

    @Bean
    @ConditionalOnMissingBean
    public HttpClient webhookHttpClient(WebhookProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }
}
