package com.jushan.boot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用配置自动装配。
 * <p>
 * 启用 {@link AppProperties} 的 {@code @ConfigurationProperties} 绑定与校验，
 * 以及 Spring 的定时任务调度（供超时停放自动拉黑等 {@code @Scheduled} 任务使用）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
