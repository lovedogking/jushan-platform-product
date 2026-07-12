package com.jushan.framework.auth;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 基础配置。
 * <p>
 * 当前阶段配置：
 * <ul>
 *   <li>全局鉴权拦截器（Spring MVC Interceptor）：除公开路径外，所有请求要求登录</li>
 *   <li>会话由 Sa-Token 自动存入 Redis（通过 {@code sa-token-redis-jackson} 自动装配）</li>
 *   <li>读取请求头 {@code Authorization} 中的 Token（由 {@code sa-token.token-name} 配置）</li>
 *   <li>鉴权异常由 {@code @RestControllerAdvice} 统一处理，返回 JSON 错误</li>
 * </ul>
 * <p>
 * <strong>注意</strong>：当 Redis 不可用时，Sa-Token 自动回退到内存存储；
 * 生产环境应确保 Redis 可用，避免会话丢失。
 * <p>
 * 使用 Spring MVC Interceptor 而非 Filter：
 * Interceptor 层异常可被 Spring {@code @RestControllerAdvice} 捕获，
 * Filter 层异常无法被捕获。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnClass(cn.dev33.satoken.SaManager.class)
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Sa-Token 鉴权拦截器（先执行，校验登录态）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // Actuator 健康检查
                        "/actuator/**",
                        // 登录接口（公开）
                        "/auth/login",
                        // 客户注册接口（公开）
                        "/register",
                        "/register/**",
                        // Demo 接口（含测试端点，仅 dev/test profile）
                        "/demo/**",
                        // 静态资源
                        "/favicon.ico",
                        "/error"
                );

        // 2. TenantContextInterceptor 已移至 TenantContextConfig 单独注册
    }
}
