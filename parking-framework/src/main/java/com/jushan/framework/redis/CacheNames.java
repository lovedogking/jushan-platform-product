package com.jushan.framework.redis;

/**
 * Spring Cache 缓存名称常量。
 * <p>
 * 用于 {@code @Cacheable(cacheNames = CacheNames.XXX)} 等注解。
 * 按业务领域划分独立缓存区域，避免不同业务数据混用同一缓存空间。
 * <p>
 * <strong>使用示例</strong>：
 * <pre>{@code
 * @Cacheable(cacheNames = CacheNames.PARKING_LOT, key = "#lotId")
 * public ParkingLotDTO getById(Long lotId) { ... }
 * }</pre>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class CacheNames {

    private CacheNames() {}

    /** 停车场信息 */
    public static final String PARKING_LOT = "parking_lot";

    /** 设备状态快照 */
    public static final String DEVICE_STATUS = "device_status";

    /** 收费规则 */
    public static final String CHARGE_RULE = "charge_rule";

    /** 用户会话（短期） */
    public static final String USER_SESSION = "user_session";

    /** 字典/配置（长期） */
    public static final String SYS_DICT = "sys_dict";

    /** 验证码 / 限流（短期） */
    public static final String CAPTCHA = "captcha";
}
