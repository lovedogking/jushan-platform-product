package com.smartparking.deviceaccess.api;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * API 模块测试配置。
 * <p>
 * {@link org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest}
 * 需要找到 {@link SpringBootConfiguration} 注解的类来确定 Spring 上下文范围。
 * 因为 API 模块不包含主启动类（在 starter 模块中），所以提供此测试专用配置。
 * <p>
 * 排除 event 子包，避免 WebMvcTest 上下文加载 EventPublisher 等依赖 registry 的 Bean。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackageClasses = {DeviceController.class, GlobalExceptionHandler.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.smartparking\\.deviceaccess\\.api\\.event\\..*"
        )
)
public class ApiTestConfiguration {
}
