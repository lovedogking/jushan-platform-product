package com.jushan.framework.auth;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 租户上下文拦截器（<strong>唯一权威上下文装配点</strong>）。
 * <p>
 * <strong>P0 安全修复（FIX-01-R1）</strong>：
 * <ol>
 *   <li>使用 {@link StpUtil#isLogin()} 判定认证状态，不再手动解析 Token 来源。
 *       与 Sa-Token 自身解析结果完全一致，消除"已认证但上下文为空"的分裂状态。</li>
 *   <li>用户身份（userType、tenantId、roles）从 User-Session 读取，
 *       与 {@code AuthService.login()} 写入方式一致。</li>
 *   <li>代理信息从 <strong>Token-Session</strong> 读取，
 *       不同 Token 的代理状态互不影响。</li>
 *   <li>在进入 Controller 前校验用户和租户状态（通过 {@link SecurityPrincipalValidator}），
 *       用户删除/停用、租户删除/停用时统一拒绝。</li>
 *   <li>userType 严格白名单校验，非法值拒绝。</li>
 * </ol>
 * <p>
 * <strong>三类身份不变量</strong>：
 * <ul>
 *   <li>platform：userType="platform"，tenantId=null，isProxy=false</li>
 *   <li>tenant：userType="tenant"，tenantId≠null，isProxy=false</li>
 *   <li>proxy：userType="platform"（真实用户），tenantId=proxyTargetTenantId，isProxy=true</li>
 * </ul>
 * <p>
 * <strong>生命周期</strong>：
 * <ul>
 *   <li>{@link #preHandle} — 填充上下文（失败时主动清理）</li>
 *   <li>{@link #afterCompletion} — 兜底清理（成功路径和 Controller 异常均执行）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantContextInterceptor.class);

    /** userType 白名单：仅允许已知合法身份类型 */
    private static final Set<String> VALID_USER_TYPES = Set.of(
            TenantContext.USER_TYPE_PLATFORM,
            TenantContext.USER_TYPE_TENANT
    );

    private final SecurityPrincipalValidator principalValidator;

    /**
     * @param principalValidator 用户/租户状态校验器（可为 null，此时跳过状态校验）
     */
    public TenantContextInterceptor(SecurityPrincipalValidator principalValidator) {
        this.principalValidator = principalValidator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 1. 通过 Sa-Token 自身判定认证状态（不手动解析 Header/Cookie）
        if (!StpUtil.isLogin()) {
            return true; // 未认证 → 公开路径或 SaInterceptor 会拦截
        }

        // 2. 获取 loginId
        Object loginIdObj = StpUtil.getLoginIdDefaultNull();
        if (loginIdObj == null) {
            // isLogin() 为 true 但 loginId 为空 → 异常状态，拒绝
            sendFailClosedResponse(response, "会话状态异常，请重新登录");
            TenantContext.clear();
            return false;
        }

        Long userId;
        try {
            userId = Long.valueOf(loginIdObj.toString());
        } catch (NumberFormatException e) {
            sendFailClosedResponse(response, "会话标识异常，请重新登录");
            TenantContext.clear();
            return false;
        }

        // 3. 校验用户状态（fail-close）
        if (principalValidator != null && !principalValidator.isUserActive(userId)) {
            // 用户已被删除或停用 → 强制登出当前 Token-Session
            try {
                StpUtil.logoutByTokenValue(StpUtil.getTokenValue());
            } catch (Exception ignored) {
                // 登出失败不阻断拒绝流程
            }
            sendFailClosedResponse(response, "账号已被禁用或不存在，请重新登录");
            TenantContext.clear();
            return false;
        }

        // 4. 从 User-Session 读取用户身份信息（与 AuthService.login() 写入方式一致）
        SaSession userSession = StpUtil.getSessionByLoginId(userId, false);
        if (userSession == null) {
            sendFailClosedResponse(response, "会话已过期，请重新登录");
            TenantContext.clear();
            return false;
        }

        String userType = toString(userSession.get(TenantContext.SESSION_KEY_USER_TYPE));
        Long sessionTenantId = toLong(userSession.get(TenantContext.SESSION_KEY_TENANT_ID));
        String roles = toString(userSession.get(TenantContext.SESSION_KEY_ROLES));

        // 5. userType 严格白名单校验
        if (userType == null || userType.isBlank()) {
            sendFailClosedResponse(response, "会话缺少用户类型信息，请重新登录");
            TenantContext.clear();
            return false;
        }
        if (!VALID_USER_TYPES.contains(userType)) {
            log.error("非法的 userType={} userId={}", userType, userId);
            sendFailClosedResponse(response, "会话身份类型异常，请重新登录");
            TenantContext.clear();
            return false;
        }

        // 6. 身份不变量校验
        if (TenantContext.USER_TYPE_PLATFORM.equals(userType) && sessionTenantId != null) {
            log.error("平台用户携带 tenantId userId={} tenantId={}", userId, sessionTenantId);
            sendFailClosedResponse(response, "会话身份异常，请重新登录");
            TenantContext.clear();
            return false;
        }
        if (TenantContext.USER_TYPE_TENANT.equals(userType) && sessionTenantId == null) {
            log.error("租户用户缺少 tenantId userId={}", userId);
            sendFailClosedResponse(response, "租户用户缺少租户绑定信息，请重新登录");
            TenantContext.clear();
            return false;
        }

        // 7. 校验租户状态（仅 tenant 类型）
        if (sessionTenantId != null && principalValidator != null
                && !principalValidator.isTenantActive(sessionTenantId)) {
            sendFailClosedResponse(response, "租户不存在或已被禁用");
            TenantContext.clear();
            return false;
        }

        // 8. 从 Token-Session 读取代理信息（绑定当前 Token，不同 Token 互不影响）
        SaSession tokenSession = StpUtil.getSession(false);
        Long proxyOperatorId = null;
        String proxyOperatorName = null;
        Long proxyTargetTenantId = null;
        boolean isProxy = false;

        if (tokenSession != null) {
            proxyOperatorId = toLong(tokenSession.get(TenantContext.SESSION_KEY_PROXY_OPERATOR_ID));
            proxyOperatorName = toString(tokenSession.get(TenantContext.SESSION_KEY_PROXY_OPERATOR_NAME));
            proxyTargetTenantId = toLong(tokenSession.get(TenantContext.SESSION_KEY_PROXY_TENANT_ID));
        }
        isProxy = proxyTargetTenantId != null && proxyOperatorId != null;

        // 9. 代理模式额外校验
        if (isProxy) {
            // 代理只允许平台身份用户启动
            if (!TenantContext.USER_TYPE_PLATFORM.equals(userType)) {
                log.error("非平台用户尝试使用代理模式 userId={} userType={}", userId, userType);
                sendFailClosedResponse(response, "会话身份异常，请重新登录");
                TenantContext.clear();
                return false;
            }
            // 校验代理目标租户状态
            if (principalValidator != null && !principalValidator.isTenantActive(proxyTargetTenantId)) {
                sendFailClosedResponse(response, "代理目标租户不存在或已被禁用");
                TenantContext.clear();
                return false;
            }
        }

        // 10. 构建并设置快照
        TenantContext.Snapshot snapshot;
        if (isProxy) {
            snapshot = new TenantContext.Snapshot(
                    proxyTargetTenantId, userId, userType, roles,
                    true, proxyOperatorId, proxyOperatorName, proxyTargetTenantId);
        } else {
            snapshot = new TenantContext.Snapshot(
                    sessionTenantId, userId, userType, roles);
        }

        TenantContext.set(snapshot);

        if (log.isDebugEnabled()) {
            log.debug("租户上下文已填充: userId={}, tenantId={}, userType={}, isProxy={}",
                    userId, snapshot.tenantId(), userType, isProxy);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    // ==================== 响应工具 ====================

    private void sendFailClosedResponse(HttpServletResponse response, String message) {
        try {
            String escaped = message.replace("\"", "'");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":401,\"message\":\"" + escaped + "\",\"data\":null}");
            response.getWriter().flush();
        } catch (Exception ignored) {
            // 响应写入失败时无法恢复
        }
    }

    // ==================== 类型转换工具 ====================

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
