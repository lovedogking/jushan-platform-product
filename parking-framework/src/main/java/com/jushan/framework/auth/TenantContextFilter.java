package com.jushan.framework.auth;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文过滤器。
 * <p>
 * <strong>已弃用（P0 安全修复）</strong>：
 * 上下文装配的唯一权威链路已迁移到 {@link TenantContextInterceptor}（Spring MVC Interceptor）。
 * 本 Filter 不再注册到 Servlet 容器，保留仅作为历史参考。
 * <p>
 * 弃用原因：
 * <ul>
 *   <li>Filter 层次运行在 SaInterceptor 之前，认证状态不可用</li>
 *   <li>硬编码 {@code Authorization} 请求头，不遵循 Sa-Token 可配置的 token-name</li>
 *   <li>Filter 层异常无法被 {@code @RestControllerAdvice} 捕获</li>
 *   <li>与 {@link TenantContextInterceptor} 和 {@link TenantContextAdvice} 形成三条竞争链路</li>
 * </ul>
 *
 * @deprecated 使用 {@link TenantContextInterceptor} 替代（在 {@code TenantContextConfig} 中注册）
 */
@Deprecated
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            populateContext(request);
        } catch (Exception e) {
            log.debug("TenantContextFilter 异常（不影响请求）: {}", e.getMessage());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void populateContext(HttpServletRequest request) {
        // 1. 提取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            return;
        }
        String token = authHeader;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2. 通过 Token 直接查询 loginId（不需要 SaTokenContext）
        Object loginIdObj;
        try {
            loginIdObj = StpUtil.getLoginIdByToken(token);
        } catch (Exception e) {
            log.debug("查询 Token loginId 失败: {}", e.getMessage());
            return;
        }
        if (loginIdObj == null) {
            return;
        }
        Long userId = Long.valueOf(loginIdObj.toString());

        // 3. 通过 loginId 获取 User-Session（与 AuthService 写入方式一致）
        SaSession session;
        try {
            session = StpUtil.getSessionByLoginId(userId, false);
        } catch (Exception e) {
            log.debug("获取 User-Session 失败: {}", e.getMessage());
            return;
        }
        if (session == null) {
            return;
        }

        // 4. 读取租户上下文信息
        Long tenantId = toLong(session.get(TenantContext.SESSION_KEY_TENANT_ID));
        String userType = toString(session.get(TenantContext.SESSION_KEY_USER_TYPE));
        String roles = toString(session.get(TenantContext.SESSION_KEY_ROLES));

        // 5. 读取代理状态
        Long proxyOperatorId = toLong(session.get(TenantContext.SESSION_KEY_PROXY_OPERATOR_ID));
        String proxyOperatorName = toString(session.get(TenantContext.SESSION_KEY_PROXY_OPERATOR_NAME));
        Long proxyTargetTenantId = toLong(session.get(TenantContext.SESSION_KEY_PROXY_TENANT_ID));
        boolean isProxy = proxyTargetTenantId != null && proxyOperatorId != null;

        if (isProxy) {
            // 代理模式下：tenantId 替换为目标租户 ID，使 DataScope 校验通过
            if (userType == null) {
                userType = TenantContext.USER_TYPE_TENANT;
            }
            TenantContext.Snapshot snapshot = new TenantContext.Snapshot(
                    proxyTargetTenantId, userId, userType, roles,
                    true, proxyOperatorId, proxyOperatorName, proxyTargetTenantId);
            TenantContext.set(snapshot);
            return;
        }

        if (userType == null) {
            userType = tenantId != null
                    ? TenantContext.USER_TYPE_TENANT
                    : TenantContext.USER_TYPE_PLATFORM;
        }

        TenantContext.Snapshot snapshot = new TenantContext.Snapshot(
                tenantId, userId, userType, roles);
        TenantContext.set(snapshot);
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}
