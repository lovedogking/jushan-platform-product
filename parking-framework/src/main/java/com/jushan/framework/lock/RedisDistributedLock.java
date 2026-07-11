package com.jushan.framework.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式锁实现。
 * <p>
 * 使用 {@code SET key value NX PX ttl} 原子命令实现。
 * 锁值使用 UUID 确保释放时只能释放自己持有的锁，防止误删。
 * <p>
 * 使用 {@link ObjectProvider} 注入 {@link StringRedisTemplate}，
 * 当 Redis 不可用时优雅降级（tryLock 返回 false）。
 * <p>
 * <strong>注意</strong>：本实现是简约版，不包含 Redisson 的看门狗自动续期、
 * 可重入等高级特性。定时任务等长持有场景需确保 leaseTime 足够。
 * 后续如需高级特性，可直接替换为 Redisson 实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    private final StringRedisTemplate stringRedisTemplate;

    /** 锁值前缀，配合 UUID 确保锁持有者唯一 */
    private static final String LOCK_VALUE_PREFIX = "lock:";

    public RedisDistributedLock(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.stringRedisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        if (stringRedisTemplate == null) {
            log.warn("Redis 不可用，无法获取分布式锁: key={}", key);
            return false;
        }

        String lockValue = LOCK_VALUE_PREFIX + UUID.randomUUID();
        long deadline = System.nanoTime() + unit.toNanos(waitTime);

        do {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, lockValue, leaseTime, unit);

            if (Boolean.TRUE.equals(success)) {
                LockContext.set(key, lockValue);
                return true;
            }

            if (waitTime > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(Math.min(100, unit.toMillis(waitTime)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        } while (System.nanoTime() < deadline);

        return false;
    }

    @Override
    public void unlock(String key) {
        if (stringRedisTemplate == null) {
            return;
        }

        String lockValue = LockContext.get(key);
        if (lockValue == null) {
            log.warn("尝试释放未持有的锁: key={}", key);
            return;
        }

        try {
            String script = """
                    if redis.call("get", KEYS[1]) == ARGV[1] then
                        return redis.call("del", KEYS[1])
                    else
                        return 0
                    end""";

            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(script);
            redisScript.setResultType(Long.class);

            Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(key), lockValue);

            if (result != null && result == 0) {
                log.debug("锁已过期或被其他持有者释放: key={}", key);
            }
        } finally {
            LockContext.remove(key);
        }
    }

    /**
     * 线程级锁值上下文 — 记录当前线程持有的锁。
     */
    private static final class LockContext {
        private static final ThreadLocal<java.util.Map<String, String>> CONTEXT =
                ThreadLocal.withInitial(java.util.HashMap::new);

        static void set(String key, String value) {
            CONTEXT.get().put(key, value);
        }

        static String get(String key) {
            return CONTEXT.get().get(key);
        }

        static void remove(String key) {
            CONTEXT.get().remove(key);
        }
    }
}
