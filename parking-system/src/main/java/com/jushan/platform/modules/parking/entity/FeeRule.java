package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收费规则实体。
 * <p>
 * 对齐 PRD V1.0 收费规则定义，继承 {@link BaseEntity} 统一规范。
 * 支持四种计费模式：1按时 2按次 3阶梯 4分时段。
 * 金额字段使用 {@link BigDecimal}，禁止浮点数。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fee_rule")
public class FeeRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属车场 ID（逻辑外键：parking_lot.id） */
    private Long lotId;

    /** 适用区域 ID（逻辑外键：parking_zone.id，NULL 表示车场通用） */
    private Long zoneId;

    /** 规则名称 */
    private String name;

    /** 规则描述 */
    private String description;

    /**
     * 计费模式：1按时(按分钟累计) 2按次 3阶梯 4分时段(按时段阶梯)。
     * 封顶计费通过 dailyCap/maxAmount 与任意模式叠加。
     */
    private Integer billingMode;

    /**
     * 适用车辆类型（逗号分隔：TEMP/MONTHLY/PREPAID/FREE/BLACKLIST），
     * NULL 表示适用所有类型。
     */
    private String vehicleType;

    /**
     * 适用车牌颜色（逗号分隔：BLUE/GREEN/YELLOW/BLACK/WHITE），
     * NULL 表示适用所有颜色。
     */
    private String plateColor;

    /** 免费时长（分钟） */
    private Integer freeMinutes;

    /** 计费单位（分钟） */
    private Integer unitMinutes;

    /** 首时段时长（分钟），0 表示无首时段优惠 */
    private Integer firstPeriodMinutes;

    /** 首时段价格 */
    @TableField(exist = true)
    private BigDecimal firstPeriodPrice;

    /** 后续单价 */
    @TableField(exist = true)
    private BigDecimal subsequentPrice;

    /** 24小时封顶金额（NULL 表示不封顶） */
    @TableField(exist = true)
    private BigDecimal dailyCap;

    /** 最大封顶金额（NULL 表示不封顶），整单封顶 */
    private BigDecimal maxAmount;

    /** 夜间封顶金额（NULL 表示不封顶） */
    @TableField(exist = true)
    private BigDecimal nightCap;

    /** 跨天计费规则：1按自然日分段（每天0点重置） 2连续计费（按总时长，每24小时一个封顶窗口） */
    private Integer crossDayMode;

    /** 生效方式：1立即生效 2仅新入场生效 3定时生效（配合 effectiveStart） */
    private Integer effectMode;

    /** 优先级，数字越大优先级越高 */
    private Integer priority;

    /** 状态：1启用 2禁用 */
    private Integer status;

    /** 生效开始时间 */
    private LocalDateTime effectiveStart;

    /** 生效结束时间 */
    private LocalDateTime effectiveEnd;

    /** 节假日特殊规则（JSON 格式） */
    private String holidayRules;

    /** 乐观锁版本号 */
    private Integer version;

    // ==================== 常量定义 ====================

    /** 计费模式：按时 */
    public static final int BILLING_MODE_TIME = 1;
    /** 计费模式：按次 */
    public static final int BILLING_MODE_PER_ENTRY = 2;
    /** 计费模式：阶梯 */
    public static final int BILLING_MODE_TIERED = 3;
    /** 计费模式：分时段 */
    public static final int BILLING_MODE_TIME_SEGMENT = 4;

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 2;

    /** 跨天计费：按自然日分段（每天0点重置封顶） */
    public static final int CROSS_DAY_NATURAL = 1;
    /** 跨天计费：连续计费（按总时长，每24小时一个封顶窗口） */
    public static final int CROSS_DAY_CONTINUOUS = 2;

    /** 生效方式：立即生效（在场车辆也按新规则） */
    public static final int EFFECT_IMMEDIATE = 1;
    /** 生效方式：仅新入场生效（已在场车辆按入场时规则） */
    public static final int EFFECT_NEW_ENTRY_ONLY = 2;
    /** 生效方式：定时生效（effectiveStart 到达后生效） */
    public static final int EFFECT_SCHEDULED = 3;
}
