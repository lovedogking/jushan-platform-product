/**
 * 平台内部 WebSocket 实时推送基础设施。
 * <p>
 * 基于 STOMP over WebSocket，为管理后台（admin-web）和岗亭端（frontend）提供：
 * <ul>
 *   <li>停车场实时事件推送（入场/出场/开闸结果）</li>
 *   <li>设备状态变更通知</li>
 *   <li>岗亭处置任务实时推送</li>
 * </ul>
 * <p>
 * <strong>安全</strong>：
 * <ul>
 *   <li>连接握手鉴权（Sa-Token）— {@link com.jushan.framework.ws.WsAuthHandshakeInterceptor}</li>
 *   <li>订阅目标数据范围校验 — {@link com.jushan.framework.ws.WsChannelAuthInterceptor}</li>
 *   <li>线程级 Session 上下文 — {@link com.jushan.framework.ws.WsSessionContext}</li>
 * </ul>
 * <p>
 * <strong>P0 红线</strong>：WebSocket 不能直连 Device Access；
 * 推送失败不得阻断入场等核心业务事务。
 */
package com.jushan.framework.ws;
