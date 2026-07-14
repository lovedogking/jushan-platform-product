package com.jushan.platform.infra.security;

import com.jushan.common.auth.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限校验切面。
 * <p>
 * 拦截 {@link RequirePermission} 注解，校验当前用户是否持有所需权限中的至少一个。
 * <ul>
 *   <li>先从 {@link TenantContext} 获取用户ID，未登录直接拒绝。</li>
 *   <li>优先使用 JWT 中携带的权限编码列表（无 Redis 依赖）。</li>
 *   <li>JWT 中无权限时，回退到 {@link PermissionProvider} 获取。</li>
 *   <li>若用户持有任一所需权限，则放行。</li>
 *   <li>否则抛出 {@link AccessDeniedException}，由全局异常处理器转为 403。</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Aspect
@Component
public class PermissionAspect {

    private final PermissionProvider permissionProvider;

    public PermissionAspect(PermissionProvider permissionProvider) {
        this.permissionProvider = permissionProvider;
    }

    /**
     * 拦截带 {@link RequirePermission} 注解的方法。
     */
    @Around("@annotation(requirePermission)")
    public Object around(ProceedingJoinPoint point, RequirePermission requirePermission) throws Throwable {
        // 1. 获取当前用户ID（未登录会抛异常）
        Long userId = TenantContext.requireUserId();

        // 2. 获取注解要求的权限
        String[] required = requirePermission.value();
        if (required == null || required.length == 0) {
            return point.proceed();
        }
        Set<String> requiredSet = new HashSet<>(Arrays.asList(required));

        // 3. 获取用户实际权限（优先从 TenantContext 的 JWT 权限获取）
        Set<String> userPermissions = getUserPermissions(userId);
        if (CollectionUtils.isEmpty(userPermissions)) {
            throw new AccessDeniedException("当前用户没有任何权限");
        }

        // 4. 校验是否有交集（* 表示拥有所有权限）
        if (userPermissions.contains("*")) {
            return point.proceed();
        }
        for (String permission : requiredSet) {
            if (userPermissions.contains(permission)) {
                return point.proceed();
            }
        }

        throw new AccessDeniedException("缺少所需权限: " + requiredSet);
    }

    /**
     * 获取当前用户权限集合。
     * <p>
     * 优先从 JWT（TenantContext）读取；若 JWT 未携带，则回退到 PermissionProvider。
     */
    private Set<String> getUserPermissions(Long userId) {
        String permissionsStr = TenantContext.getPermissions();
        if (StringUtils.hasText(permissionsStr)) {
            return Arrays.stream(permissionsStr.split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toSet());
        }
        return permissionProvider.getPermissions(userId);
    }
}
