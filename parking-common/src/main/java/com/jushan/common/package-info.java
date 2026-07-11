/**
 * parking-common — 通用基础类型模块。
 * <p>
 * 本模块不依赖 Spring 容器，仅包含：
 * <ul>
 *   <li>统一响应结构 {@code R<T>}</li>
 *   <li>错误码枚举 {@code ErrorCode}</li>
 *   <li>业务异常 {@code BusinessException}</li>
 *   <li>金额类型 {@code Money}（整数分）</li>
 *   <li>通用工具类</li>
 * </ul>
 * <p>
 * 所有业务模块和 parking-framework 均可安全依赖本模块。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
package com.jushan.common;
