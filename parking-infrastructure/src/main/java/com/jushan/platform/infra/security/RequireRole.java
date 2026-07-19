package com.jushan.platform.infra.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解。
 * <p>
 * 标记在 Controller 方法或类上，要求当前登录用户必须是指定角色之一。
 * 支持单个角色或角色数组，满足任意一个即可通过。
 * <p>
 * 角色值对应 {@link com.jushan.common.auth.TenantContext.Snapshot#userType()}：
 * <ul>
 *   <li>{@code platform} — 超级管理员</li>
 *   <li>{@code tenant} — 租户管理员</li>
 *   <li>{@code booth} — 岗亭管理员</li>
 * </ul>
 * <p>
 * 与 {@link RequirePermission} 配合使用，形成「角色 + 权限码」双重校验。
 *
 * @author Jushan Platform
 * @since 1.3.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 所需角色。
     * <p>
     * 可传入单个字符串或字符串数组，当前用户持有任意一个即可放行。
     */
    String[] value() default {};
}
