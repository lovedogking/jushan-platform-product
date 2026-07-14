package com.jushan.platform.infra.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。
 * <p>
 * 标记在 Controller 方法或 Service 方法上，要求当前登录用户必须持有指定权限码中的至少一个。
 * 支持单个权限码或权限码数组，满足任意一个即可通过。
 * <p>
 * 权限码格式建议为 {@code 模块:操作}，例如：
 * <ul>
 *   <li>{@code company:view} — 查看公司</li>
 *   <li>{@code company:create} — 新增公司</li>
 *   <li>{@code account:delete} — 删除账号</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 所需权限码。
     * <p>
     * 可传入单个字符串或字符串数组，当前用户持有任意一个即可放行。
     */
    String[] value() default {};
}
