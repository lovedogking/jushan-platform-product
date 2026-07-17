package com.jushan.system.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 车辆名单缓存存储。
 * 遵循项目 RedisParamCacheStore 的双层缓存模式：
 * Redis 可用 → Redis String；Redis 不可用 → 进程内 ConcurrentHashMap 降级。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Component
public class VehicleListCacheStore {

    private static final Logger log = LoggerFactory.getLogger(VehicleListCacheStore.class);

    static final String KEY_PREFIX = "vehicle_list:";
    static final long TTL_MINUTES = 30;
    static final String NULL_MARKER = "__NULL__";

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, String> localCache = new ConcurrentHashMap<>();

    public VehicleListCacheStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.stringRedisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * 构建缓存键：vehicle_list:{parkingLotId}:{plateNumber}
     */
    public static String buildKey(Long parkingLotId, String plateNumber) {
        return parkingLotId + ":" + plateNumber;
    }

    public String get(Long parkingLotId, String plateNumber) {
        String key = buildKey(parkingLotId, plateNumber);
        if (stringRedisTemplate == null) {
            return localCache.get(key);
        }
        try {
            return stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("名单缓存读取异常，回退本地缓存: lotId={} plate={}", parkingLotId, plateNumber, e);
            return localCache.get(key);
        }
    }

    public void put(Long parkingLotId, String plateNumber, String value) {
        String key = buildKey(parkingLotId, plateNumber);
        String val = value != null ? value : NULL_MARKER;
        if (stringRedisTemplate == null) {
            localCache.put(key, val);
            return;
        }
        try {
            stringRedisTemplate.opsForValue()
                    .set(KEY_PREFIX + key, val, Duration.ofMinutes(TTL_MINUTES));
        } catch (Exception e) {
            log.warn("名单缓存写入异常（忽略）: lotId={} plate={}", parkingLotId, plateNumber, e);
            localCache.put(key, val);
        }
    }

    public void evict(Long parkingLotId, String plateNumber) {
        String key = buildKey(parkingLotId, plateNumber);
        if (stringRedisTemplate == null) {
            localCache.remove(key);
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("名单缓存失效异常（忽略）: lotId={} plate={}", parkingLotId, plateNumber, e);
        }
        localCache.remove(key);
    }
}
