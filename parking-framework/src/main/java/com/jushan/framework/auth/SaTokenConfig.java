package com.jushan.framework.auth;

import cn.dev33.satoken.filter.SaServletFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 基础配置。
 * <p>
 * 当前阶段仅建立框架层面最小配置：
 * <ul>
 *   <li>全局鉴权过滤器（所有非放行路由要求登录）</li>
 *   <li>会话由 Sa-Token 自动存入 Redis（通过 {@code sa-token-redis-jackson} 自动装配）</li>
 *   <li>后续 T12 补充登录、角色、权限模型</li>
 * </ul>
 * <p>
 * <strong>放行路径可随业务模块扩展</strong>，当前仅放行 Actuator、Demo 和静态资源。
 * <p>
 * <strong>注意</strong>：当 Redis 不可用时，Sa-Token 自动回退到内存存储；
 * 生产环境应确保 Redis 可用，避免会话丢失导致用户被踢出。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(cn.dev33.satoken.SaManager.class)
public class SaTokenConfig {

    private static final Logger log = LoggerFactory.getLogger(SaTokenConfig.class);

    /**
     * 注册 Sa-Token 全局过滤器。
     * <p>
     * 对所有请求进行路由鉴权，排除公开路径后，其余一律要求登录。
     * 后续 T12 增加角色和权限校验后，在此过滤器中补充。
     */
    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
                .addInclude("/**")
                .addExclude(
                        // Actuator 健康检查
                        "/actuator/**",
                        // Demo 接口（T05 验收用，后续可收紧）
                        "/demo/**",
                        // 静态资源
                        "/favicon.ico",
                        "/error"
                )
                .setAuth(obj -> {
                    // T12 实现登录后取消注释下一行：
                    // SaRouter.match("/**").check(r -> StpUtil.checkLogin());
                    log.debug("Sa-Token 全局过滤器已注册（登录校验将在 T12 启用）");
                });
    }
}
