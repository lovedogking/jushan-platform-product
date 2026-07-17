package com.smartparking.deviceaccess.config;

import com.smartparking.deviceaccess.api.auth.ApiKeyAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet Filter 注册配置。
 *
 * @since v0.4
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(ApiKeyAuthFilter filter) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(100);
        registration.setName("apiKeyAuthFilter");
        return registration;
    }
}
