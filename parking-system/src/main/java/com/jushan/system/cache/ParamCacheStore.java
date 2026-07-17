package com.jushan.system.cache;

/**
 * 参数解析缓存存储抽象（任务包 1-1）。
 * <p>
 * {@code ParamResolver} 通过本接口读写参数缓存，将缓存介质与解析逻辑解耦，
 * 便于在无 Redis 环境（单元测试 / 降级）下仍可验证"变更后立即失效"的契约。
 * <p>
 * 语义约定：
 * <ul>
 *   <li>{@link #get(String)} 返回 {@code null} 表示<b>未缓存</b>（缓存未命中）。</li>
 *   <li>{@link #put(String, String)} 写入的 value 可为调用方自定义的"已知缺省"哨兵字符串，
 *       用于缓存"该键无值"这一事实，避免缓存穿透。</li>
 *   <li>{@link #evict(String)} 立即删除对应缓存键。</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public interface ParamCacheStore {

    /**
     * 读取缓存值。
     *
     * @param key 缓存键
     * @return 缓存值；未缓存时返回 {@code null}
     */
    String get(String key);

    /**
     * 写入缓存值（带默认 TTL）。
     *
     * @param key   缓存键
     * @param value 缓存值（不可为 {@code null}，缺省场景请写入哨兵字符串）
     */
    void put(String key, String value);

    /**
     * 立即失效指定缓存键。
     *
     * @param key 缓存键
     */
    void evict(String key);
}
