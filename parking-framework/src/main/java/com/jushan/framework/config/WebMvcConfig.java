package com.jushan.framework.config;

import com.jushan.framework.web.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;

/**
 * Web MVC 通用配置。
 * <p>
 * 注册 TraceIdFilter、CharacterEncodingFilter 等全局过滤器。
 * <p>
 * <strong>P0 安全修复</strong>：TenantContextFilter 已移除注册。
 * 租户上下文装配的唯一权威链路现在是 {@code TenantContextInterceptor}（在
 * {@code TenantContextConfig} 中注册为 Spring MVC Interceptor），
 * 在 SaInterceptor 鉴权通过后从 User-Session 读取可信上下文。
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

    /**
     * 注册 CharacterEncodingFilter，强制请求/响应使用 UTF-8 编码。
     * <p>
     * 防止中文在 HTTP 响应中被错误解析为 ISO-8859-1 导致乱码。
     * 优先级紧随 TraceIdFilter 之后。
     */
    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> characterEncodingFilterRegistration() {
        FilterRegistrationBean<CharacterEncodingFilter> registration = new FilterRegistrationBean<>();
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceRequestEncoding(true);
        filter.setForceResponseEncoding(true);
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        // 注意：不要使用 "characterEncodingFilter" 这个名字，
        // Spring Boot 3.x HttpEncodingAutoConfiguration 已自动注册同名的 Filter。
        registration.setName("customCharacterEncodingFilter");
        return registration;
    }
}
