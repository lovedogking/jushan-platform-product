package com.jushan.platform.infra.security;

import com.jushan.common.auth.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器。
 * <p>
 * 从请求头 {@code Authorization: Bearer {token}} 提取 JWT，验证后设置安全上下文和租户上下文。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret:jushan-platform-secret-key-for-jwt-token-generation-must-be-at-least-32-chars}")
    private String secretKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            
            if (StringUtils.hasText(token) && JwtUtils.validateToken(token, secretKey)) {
                var claims = JwtUtils.parseClaims(token, secretKey);

                // audience 校验：/api/v1/mini/** 路径要求 aud=miniapp
                String audience = JwtUtils.getAudienceFromClaims(claims);
                String requestPath = request.getRequestURI();
                if (requestPath.startsWith("/api/v1/mini/")
                        && !"miniapp".equals(audience)) {
                    log.warn("JWT audience 校验失败: path={}, aud={}", requestPath, audience);
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = JwtUtils.getUserIdFromClaims(claims);
                Long tenantId = JwtUtils.getTenantIdFromClaims(claims);
                String userType = JwtUtils.getUserTypeFromClaims(claims);
                String roles = JwtUtils.getRolesFromClaims(claims);
                String permissions = JwtUtils.getPermissionsFromClaims(claims);

                // 设置 Spring Security 上下文：权限来自 JWT 中的 permissions
                List<SimpleGrantedAuthority> authorities;
                if (permissions != null && !permissions.isBlank()) {
                    authorities = Arrays.stream(permissions.split(","))
                            .map(String::trim)
                            .filter(p -> !p.isEmpty())
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                } else {
                    authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 设置租户上下文
                TenantContext.Snapshot snapshot = new TenantContext.Snapshot(tenantId, userId, userType, roles, permissions);
                TenantContext.set(snapshot);

                log.debug("用户 {} 认证成功，租户ID: {}", userId, tenantId);
            }
        } catch (Exception e) {
            log.error("JWT 认证失败", e);
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理上下文
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    /**
     * 从请求头或 URL 查询参数提取 Token。
     * <p>
     * WebSocket/SockJS 握手无法自定义 Header，因此支持 {@code ?token={jwt}} 方式。
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
