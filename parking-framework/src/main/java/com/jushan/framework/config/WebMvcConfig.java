package com.jushan.framework.config;

import com.jushan.framework.web.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Web MVC 通用配置。
 * <p>
 * 注册 TraceIdFilter 等全局过滤器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
public class WebMvcConfig {

    /**
     * 注册 TraceId 过滤器，设为最高优先级，确保最早执行。
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("traceIdFilter");
        return registration;
    }
}
