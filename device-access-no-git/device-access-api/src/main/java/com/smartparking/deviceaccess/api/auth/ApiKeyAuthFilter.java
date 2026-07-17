package com.smartparking.deviceaccess.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API Key 认证过滤器。
 *
 * <p>拦截 {@code /api/v1/**} 请求，校验 {@code X-API-Key} Header；
 * 放行 {@code /actuator/health} 等健康检查端点。
 *
 * @since v0.4
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!properties.isValid(apiKey)) {
            log.warn("API Key authentication failed: path={}, remoteAddr={}, headerPresent={}",
                    path, request.getRemoteAddr(), apiKey != null);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized: invalid or missing X-API-Key\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExempt(String path) {
        return path.startsWith("/actuator/health");
    }
}
