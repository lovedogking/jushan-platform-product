package com.jushan.platform.infra.security;

import com.jushan.common.auth.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 角色校验切面。
 * <p>
 * 拦截 {@link RequireRole} 注解，校验当前用户角色是否匹配。
 * <ul>
 *   <li>先从 {@link TenantContext} 获取 userType</li>
 *   <li>未登录（userType == null）直接拒绝</li>
 *   <li>若用户角色匹配允许列表中的任意一个，则放行</li>
 *   <li>否则抛出 {@link AccessDeniedException}，由全局异常处理器转为 HTTP 403</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.3.0
 */
@Aspect
@Component
public class RoleAspect {

    /**
     * 拦截带 {@link RequireRole} 注解的方法。
     */
    @Around("@annotation(requireRole)")
    public Object around(ProceedingJoinPoint point, RequireRole requireRole) throws Throwable {
        String[] allowed = requireRole.value();
        if (allowed == null || allowed.length == 0) {
            return point.proceed();
        }

        String userType = TenantContext.getUserType();
        if (userType == null) {
            throw new AccessDeniedException("未登录或会话已过期，无法确定用户角色");
        }

        for (String role : allowed) {
            if (role.equals(userType)) {
                return point.proceed();
            }
        }

        throw new AccessDeniedException(
                "当前用户角色 [" + userType + "] 无权限访问此接口，需要角色: "
                        + Arrays.toString(allowed));
    }
}
