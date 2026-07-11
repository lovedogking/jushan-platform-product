package com.jushan.framework.mq;

import java.time.Duration;

/**
 * 消息消费幂等接口。
 * <p>
 * 所有异步消息消费者在执行业务逻辑前必须先调用 {@link #isProcessed(String)} 判定，
 * 处理成功后立即调用 {@link #markProcessed(String, Duration)} 记录。
 * <p>
 * 幂等基于 messageId（来自 {@link MessageEnvelope#getMessageId()}），
 * 与业务层事件幂等（eventId）互补：
 * <ul>
 *   <li>messageId 幂等 — 防止同一条 RabbitMQ 消息被重复投递</li>
 *   <li>业务 eventId 幂等 — 防止相同业务事件被重复创建记录</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface MessageIdempotency {

    /**
     * 检查消息是否已被处理。
     *
     * @param messageId 消息唯一标识
     * @return true 已处理（应跳过），false 未处理（可继续）
     */
    boolean isProcessed(String messageId);

    /**
     * 标记消息已处理。
     *
     * @param messageId 消息唯一标识
     * @param ttl       幂等记录保留时间（超期后自动清理，防止 Key 堆积）
     */
    void markProcessed(String messageId, Duration ttl);
}
