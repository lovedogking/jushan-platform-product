package com.jushan.system.mybatis;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.common.mybatis.TenantIgnoreHolder;
import com.jushan.system.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@link TenantIgnore} 注解的 AOP 切面。
 * <p>
 * 在方法执行前校验调用方身份（仅平台用户可跳过租户拦截），
 * 设置 {@link TenantIgnoreHolder}，并在方法执行后清理。
 * 同时调用 {@link AuditService#writeAuditLog} 记录跨租户查询审计。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Aspect
@Component
public class TenantIgnoreAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantIgnoreAspect.class);

    private final AuditService auditService;

    public TenantIgnoreAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(tenantIgnore)")
    public Object around(ProceedingJoinPoint pjp, TenantIgnore tenantIgnore) throws Throwable {
        TenantContext.Snapshot ctx = TenantContext.get();

        // fail-close：已登录的租户用户禁止 bypass；匿名和平台用户允许
        if (ctx != null && !ctx.isPlatformUser()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "租户用户禁止跨租户访问");
        }

        if (ctx != null && tenantIgnore.audit()) {
            writeAuditLog(pjp, tenantIgnore.reason());
        }

        TenantIgnoreHolder.set(true);
        try {
            return pjp.proceed();
        } finally {
            TenantIgnoreHolder.clear();
        }
    }

    private void writeAuditLog(ProceedingJoinPoint pjp, String reason) {
        try {
            String clientIp = extractClientIp();
            auditService.writeAuditLog(
                    null,
                    "cross_tenant_query",
                    pjp.getSignature().toShortString(),
                    "cross_tenant_query",
                    null,
                    null,
                    AuditService.RESULT_SUCCESS,
                    "",
                    reason,
                    clientIp
            );
        } catch (Exception e) {
            log.warn("跨租户查询审计日志写入失败: {}", e.getMessage());
            // 审计失败不阻塞跨租户查询本身
        }
    }

    private String extractClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "";
    }
}
