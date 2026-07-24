package com.jushan.common.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明当前方法可跳过 MyBatis-Plus 租户拦截器，用于平台用户的跨租户查询接口。
 * <p>
 * 使用限制（fail-close）：
 * <ul>
 *   <li>仅允许平台用户（{@code TenantContext.isPlatformUser() == true}）调用。</li>
 *   <li>租户用户调用将直接抛出 {@code FORBIDDEN}。</li>
 *   <li>必须填写 {@link #reason()}，用于审计说明。</li>
 *   <li>默认自动写入审计日志，可通过 {@link #audit()} 关闭。</li>
 * </ul>
 * <p>
 * 典型场景：平台总后台查询全平台审计日志、租户注册初始化等。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantIgnore {

    /**
     * 跨租户访问原因，必填，用于审计。
     */
    String reason();

    /**
     * 是否写入审计日志，默认开启。
     */
    boolean audit() default true;
}
