package com.jushan.platform.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 拦截器配置。
 * <p>
 * 在 parking-system 模块中注册 Spring MVC HandlerInterceptor。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Configuration
public class WebInterceptorConfig implements WebMvcConfigurer {
}
