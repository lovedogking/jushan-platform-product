package com.jushan.framework.auth;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.lang.reflect.Type;

/**
 * 租户上下文 Advice（防御性安全网）。
 * <p>
 * <strong>设计变更（P0 安全修复）</strong>：
 * 上下文装配的唯一权威链路已迁移到 {@link TenantContextInterceptor}。
 * 本 Advice 不再主动填充上下文，仅作为防御性检查：
 * <ul>
 *   <li>如果 Interceptor 已填充上下文 → 跳过（本 Advice 不做二次覆盖）</li>
 *   <li>如果上下文意外为空且用户已认证 → 记录 WARN 日志但不填充
 *       （交由 Controller 层的 {@code TenantContext.requireTenantId()} 进行 fail-close）</li>
 * </ul>
 * <p>
 * <strong>已修复的 Bug</strong>：此前使用 {@code StpUtil.getSession(false)}（Token-Session）
 * 读取租户上下文，但 {@code AuthService.login()} 写入的目标是 User-Session
 * （{@code StpUtil.getSessionByLoginId(userId)}），两者为不同 Session，导致上下文永远读不到。
 * 现已移除该错误读取路径。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ControllerAdvice
public class TenantContextAdvice implements RequestBodyAdvice {

    private static final Logger log = LoggerFactory.getLogger(TenantContextAdvice.class);

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage,
                                           MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) {
        verifyContextState();
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
                                MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage,
                                  MethodParameter parameter, Type targetType,
                                  Class<? extends HttpMessageConverter<?>> converterType) {
        verifyContextState();
        return body;
    }

    /**
     * 防御性检查：确认上下文状态与认证状态一致。
     * <p>
     * 如果用户已通过 Sa-Token 认证但上下文为空，记录 WARN 日志。
     * 不做自动填充——统一由 {@link TenantContextInterceptor} 负责，
     * Controller 层通过 {@code TenantContext.requireTenantId()} 实现 fail-close。
     */
    private void verifyContextState() {
        if (TenantContext.get() != null) {
            return; // Interceptor 已正确填充
        }

        // 上下文为空但用户已认证 → 防御性告警
        if (StpUtil.isLogin()) {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            log.warn("TenantContextAdvice 防御性告警：用户已认证但上下文为空，"
                    + "userId={}。上下文应由 TenantContextInterceptor 填充。", loginId);
        }
    }
}
