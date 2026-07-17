package com.smartparking.deviceaccess.api.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API Key 认证模块配置。
 *
 * @since v0.4
 */
@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class AuthConfig {

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter(ApiKeyProperties properties) {
        return new ApiKeyAuthFilter(properties);
    }
}
