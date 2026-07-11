package com.jushan.framework.lock;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁接口。
 * <p>
 * 用于跨实例、跨线程的互斥控制。典型使用场景：
 * <ul>
 *   <li>定时任务防重执行</li>
 *   <li>资源并发更新保护</li>
 *   <li>幂等性保障</li>
 * </ul>
 * <p>
 * <strong>使用示例</strong>：
 * <pre>{@code
 * String lockKey = RedisKeyPrefix.LOCK + "order:" + orderId;
 * if (lock.tryLock(lockKey, 0, 10, TimeUnit.SECONDS)) {
 *     try {
 *         // 临界区
 *     } finally {
 *         lock.unlock(lockKey);
 *     }
 * }
 * }</pre>
 * <p>
 * 当前默认实现为 {@code RedisDistributedLock}，
 * 后续可按需替换为 Redisson、Zookeeper 等实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface DistributedLock {

    /**
     * 尝试获取锁。
     *
     * @param key       锁标识（建议使用 {@link com.jushan.framework.redis.RedisKeyPrefix} 规范命名）
     * @param waitTime  最大等待时间（0 表示不等待，立即返回）
     * @param leaseTime 锁持有时间（超过后自动释放，防止死锁）
     * @param unit      时间单位
     * @return true 获取成功，false 获取失败
     */
    boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 释放锁。
     *
     * @param key 锁标识
     */
    void unlock(String key);
}
