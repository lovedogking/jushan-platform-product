package com.jushan.boot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用配置自动装配。
 * <p>
 * 启用 {@link AppProperties} 的 {@code @ConfigurationProperties} 绑定与校验。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
