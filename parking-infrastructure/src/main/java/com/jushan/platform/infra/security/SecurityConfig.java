package com.jushan.platform.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类。
 * <p>
 * 配置无状态认证（JWT），禁用 CSRF，放行登录接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 配置安全过滤链。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（使用 JWT 无状态认证）
            .csrf(csrf -> csrf.disable())
            
            // 无状态会话
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 授权规则
            .authorizeHttpRequests(auth -> auth
                // 放行登录、验证码等公开接口
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/captcha").permitAll()
                // 微信登录与注册
                .requestMatchers("/wx/login").permitAll()
                // 小程序认证接口（无需登录）
                .requestMatchers("/api/v1/mini/login", "/api/v1/mini/phone").permitAll()
                .requestMatchers("/register", "/register/**").permitAll()
                .requestMatchers("/api/v1/register").permitAll()
                // 演示与内部 mock 接口
                .requestMatchers("/demo/**").permitAll()
                .requestMatchers("/api/v1/internal/mock/**").permitAll()
                // 岗亭端登录转发（frontend 通过 /auth/login 访问）
                .requestMatchers("/auth/login").permitAll()
                // Device Access Webhook 接收端（Device Access 系统调用，无需用户认证）
                .requestMatchers("/api/v1/device-webhook/**").permitAll()
                // 支付回调接口（外部系统调用，无需认证）
                .requestMatchers("/api/v1/orders/notify", "/api/v1/pay/notify", "/api/v1/pay/callback").permitAll()
                // 健康检查与静态资源
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/favicon.ico", "/error").permitAll()
                // springdoc-openapi / Swagger UI 文档
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // WebSocket STOMP 端点公开（握手鉴权由 WsAuthHandshakeInterceptor 负责）
                .requestMatchers("/ws/**").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
            
            // 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
