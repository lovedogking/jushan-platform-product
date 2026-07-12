package com.jushan.framework.auth;

/**
 * 安全主体校验接口（框架层抽象）。
 * <p>
 * 由 parking-system 模块提供实现，在上下文装配时校验用户和租户的当前服务端状态。
 * <p>
 * <strong>调用时机</strong>：{@link TenantContextInterceptor} 在填充上下文之前，
 * 通过本接口确认用户和租户未被删除或停用。
 * <p>
 * 本接口定义在框架层以避免循环依赖：框架层不可直接依赖系统层的 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SecurityPrincipalValidator {

    /**
     * 校验用户是否存在且状态允许登录和请求。
     *
     * @param userId 用户 ID
     * @return true 如果用户存在且状态为 ENABLED
     */
    boolean isUserActive(Long userId);

    /**
     * 校验租户是否存在且状态允许业务请求。
     *
     * @param tenantId 租户 ID
     * @return true 如果租户存在且状态为 ENABLED
     */
    boolean isTenantActive(Long tenantId);
}
