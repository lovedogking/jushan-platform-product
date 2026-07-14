package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车辆进出策略配置实体。
 * <p>
 * 键值对存储策略配置，支持入场/出场/黑名单/VIP等多种策略类型。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("access_policy")
public class AccessPolicy extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 停车场ID */
    private Long parkingLotId;

    /** 策略类型：ENTRY-入场策略, EXIT-出场策略, BLACKLIST-黑名单策略, VIP-VIP策略 */
    private String policyType;

    /** 策略键 */
    private String policyKey;

    /** 策略值 */
    private String policyValue;

    /** 策略说明 */
    private String description;

    /** 排序 */
    private Integer sortOrder;

    /** 状态：ACTIVE-生效, DISABLED-已禁用 */
    private String status;

    // ==================== 常量定义 ====================

    public static final String TYPE_ENTRY = "ENTRY";
    public static final String TYPE_EXIT = "EXIT";
    public static final String TYPE_BLACKLIST = "BLACKLIST";
    public static final String TYPE_VIP = "VIP";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    // 常用策略键
    public static final String KEY_ALLOW_ENTRY = "allow_entry";
    public static final String KEY_AUTO_RELEASE = "auto_release";
    public static final String KEY_NEED_CONFIRM = "need_confirm";
    public static final String KEY_FREE_MINUTES = "free_minutes";
    public static final String KEY_MAX_STAY_HOURS = "max_stay_hours";
    public static final String KEY_DUPLICATE_ENTRY_POLICY = "duplicate_entry_policy";
}
