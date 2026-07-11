package com.jushan.framework.redis;

/**
 * Redis Key 前缀常量。
 * <p>
 * 所有 Redis Key 按层级命名：{@code jushan:模块:子模块:具体标识}。
 * 全局前缀 {@code jushan:} 由 {@link RedisConfig} 统一附加，
 * 本类中的常量是相对前缀（不含全局前缀）。
 * <p>
 * <strong>命名规范</strong>：
 * <ul>
 *   <li>使用冒号分隔层级</li>
 *   <li>全小写，单词间用短横线连接</li>
 *   <li>按领域聚合，避免跨领域 Key 混用</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class RedisKeyPrefix {

    private RedisKeyPrefix() {}

    // ==================== 会话与认证 ====================

    /** Sa-Token 会话（由 Sa-Token 框架管理，此处仅记录） */
    public static final String AUTH = "auth:";

    /** 登录失败次数限制 */
    public static final String LOGIN_FAIL = "auth:login-fail:";

    /** 用户会话缓存 */
    public static final String USER_SESSION = "auth:user-session:";

    // ==================== 分布式锁 ====================

    /** 分布式锁 */
    public static final String LOCK = "lock:";

    // ==================== 业务缓存 ====================

    /** 停车场信息 */
    public static final String PARKING_LOT = "biz:parking-lot:";

    /** 设备状态快照 */
    public static final String DEVICE_STATUS = "biz:device-status:";

    /** 收费规则版本 */
    public static final String CHARGE_RULE = "biz:charge-rule:";

    // ==================== 通用 ====================

    /** 通用数据缓存（各模块自行追加子级） */
    public static final String CACHE = "cache:";

    /** 计数器 / 限流 */
    public static final String RATE_LIMIT = "rate-limit:";

    // ==================== 消息与异步 ====================

    /** 消息消费幂等标记 */
    public static final String MQ_CONSUMED = "mq:consumed:";

    /** WebSocket 会话与连接 */
    public static final String WS_SESSION = "ws:session:";
}
