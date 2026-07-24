package com.jushan.platform.modules.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 的参数缓存存储实现（任务包 1-1）。
 * <p>
 * 遵循项目现有 Redis 缓存方案（{@code ObjectProvider<StringRedisTemplate>} 优雅降级，
 * 见 {@code RedisMessageIdempotency} / {@code RedisDistributedLock}）：
 * <ul>
 *   <li>Redis 可用时：读写 {@code jushan:param:*} 键，默认 TTL {@value #TTL_MINUTES} 分钟。</li>
 *   <li>Redis 不可用时（{@code StringRedisTemplate} 为 null）：降级为进程内 {@link ConcurrentHashMap}，
 *       保证功能可用与单元测试可验证性（无跨实例共享，与全局缓存降级口径一致）。</li>
 * </ul>
 * Key 前缀 {@value #KEY_PREFIX}，最终键形如 {@code jushan:param:lot:{lotId}:{key}}。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Component
public class RedisParamCacheStore implements ParamCacheStore {

    private static final Logger log = LoggerFactory.getLogger(RedisParamCacheStore.class);

    /** 参数缓存 Key 前缀（RedisTemplate 会再叠加全局前缀 {@code jushan:}）。 */
    static final String KEY_PREFIX = "param:";

    /** 默认 TTL（分钟）。 */
    static final long TTL_MINUTES = 30;

    private final StringRedisTemplate stringRedisTemplate;

    /** Redis 不可用时的进程内降级缓存。 */
    private final ConcurrentHashMap<String, String> localCache = new ConcurrentHashMap<>();

    public RedisParamCacheStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.stringRedisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    public String get(String key) {
        if (key == null) {
            return null;
        }
        if (stringRedisTemplate == null) {
            return localCache.get(key);
        }
        try {
            return stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("参数缓存读取失败，回退数据库: key={}", key, e);
            return null;
        }
    }

    @Override
    public void put(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        if (stringRedisTemplate == null) {
            localCache.put(key, value);
            return;
        }
        try {
            stringRedisTemplate.opsForValue()
                    .set(KEY_PREFIX + key, value, Duration.ofMinutes(TTL_MINUTES));
        } catch (Exception e) {
            log.warn("参数缓存写入失败（忽略）: key={}", key, e);
        }
    }

    @Override
    public void evict(String key) {
        if (key == null) {
            return;
        }
        if (stringRedisTemplate == null) {
            localCache.remove(key);
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("参数缓存失效失败（忽略）: key={}", key, e);
        }
    }
}
