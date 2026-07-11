/**
 * 平台内部 RabbitMQ 异步消息基础设施。
 * <p>
 * 包含：
 * <ul>
 *   <li>{@link com.jushan.framework.mq.MessageEnvelope} — 统一消息信封</li>
 *   <li>{@link com.jushan.framework.mq.MqConstants} — Exchange/Queue/RoutingKey 常量</li>
 *   <li>{@link com.jushan.framework.mq.RabbitMqConfig} — RabbitMQ 配置与死信策略</li>
 *   <li>{@link com.jushan.framework.mq.MessageIdempotency} — 消息消费幂等接口</li>
 *   <li>{@link com.jushan.framework.mq.RedisMessageIdempotency} — 基于 Redis 的幂等实现</li>
 * </ul>
 * <p>
 * <strong>P0 红线</strong>：本包中的所有内容仅用于平台内部异步任务和消息路由。
 * 不得创建或声称使用 Device Access 的 Exchange、Queue、RoutingKey 或车牌事件。
 */
package com.jushan.framework.mq;
