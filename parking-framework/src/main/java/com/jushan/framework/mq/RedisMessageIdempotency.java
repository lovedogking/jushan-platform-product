package com.jushan.framework.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的消息消费幂等实现。
 * <p>
 * 使用 Redis {@code SETNX} 原子操作记录已处理消息：
 * <ul>
 *   <li>{@link #isProcessed(String)} — {@code EXISTS key} 判定</li>
 *   <li>{@link #markProcessed(String, Duration)} — {@code SET key "1" NX EX ttl-seconds}</li>
 * </ul>
 * <p>
 * Key 格式：{@code jushan:mq:consumed:{messageId}}（TTL 默认 72 小时）。
 * <p>
 * Redis 不可用时（{@code StringRedisTemplate} 为 null）：
 * <ul>
 *   <li>{@code isProcessed} 返回 false（不过滤，允许消费）</li>
 *   <li>{@code markProcessed} 记录 warning 日志但不阻断流程</li>
 * </ul>
 * <p>
 * <strong>注意</strong>：Redis 不可用时幂等保证降级为"尽量处理"，
 * 业务层的 eventId 幂等（数据库唯一约束）提供最终防护。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisMessageIdempotency implements MessageIdempotency {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageIdempotency.class);

    /** 幂等 Key 前缀 */
    static final String KEY_PREFIX = "mq:consumed:";

    /** 默认 TTL：72 小时 */
    static final Duration DEFAULT_TTL = Duration.ofHours(72);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisMessageIdempotency(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.stringRedisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    public boolean isProcessed(String messageId) {
        if (stringRedisTemplate == null) {
            log.warn("Redis 不可用，跳过幂等检查: messageId={}", messageId);
            return false;
        }

        String key = KEY_PREFIX + messageId;
        Boolean exists = stringRedisTemplate.hasKey(key);
        boolean processed = Boolean.TRUE.equals(exists);

        if (processed) {
            log.info("重复消息已跳过: messageId={}", messageId);
        }
        return processed;
    }

    @Override
    public void markProcessed(String messageId, Duration ttl) {
        if (stringRedisTemplate == null) {
            log.warn("Redis 不可用，未记录幂等标记: messageId={}", messageId);
            return;
        }

        Duration effectiveTtl = (ttl != null) ? ttl : DEFAULT_TTL;
        String key = KEY_PREFIX + messageId;

        // SET key "1" NX EX ttl-seconds → 只有 key 不存在时才写入
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", effectiveTtl);

        if (Boolean.TRUE.equals(success)) {
            log.debug("消息处理完成，已标记幂等: messageId={}", messageId);
        } else {
            log.info("消息幂等标记已存在（并发标记）: messageId={}", messageId);
        }
    }
}
