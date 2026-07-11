/**
 * parking-framework — 平台横切能力模块。
 * <p>
 * 本模块依赖 Spring，为所有业务模块提供：
 * <ul>
 *   <li>全局异常处理</li>
 *   <li>TraceId 过滤器</li>
 *   <li>可信租户上下文 {@code TenantContext}</li>
 *   <li>Sa-Token 权限集成</li>
 *   <li>日志脱敏</li>
 *   <li>幂等注解</li>
 *   <li>WebSocket 鉴权</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
package com.jushan.framework;
