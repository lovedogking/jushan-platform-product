package com.jushan.framework.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 租户上下文拦截器配置。
 * <p>
 * 注册 {@link TenantContextInterceptor} 为 Spring MVC HandlerInterceptor，
 * 在 SaInterceptor 鉴权通过后自动填充 {@link TenantContext}。
 * <p>
 * <strong>P0 安全修复（FIX-01-R1）</strong>：
 * {@link SecurityPrincipalValidator} 通过构造器注入（可选依赖），
 * 用于在校验时确认用户和租户的当前服务端状态。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class TenantContextConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private SecurityPrincipalValidator principalValidator;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantContextInterceptor(principalValidator))
                .addPathPatterns("/**");
    }
}
