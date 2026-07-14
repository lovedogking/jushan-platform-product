package com.jushan.platform.modules.parking.entity;

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

    /**
     * 计费模式：1按时 2按次 3阶梯 4分时段
     */
    private Integer billingMode;

    /** 免费时长（分钟） */
    private Integer freeMinutes;

    /** 计费单位（分钟） */
    private Integer unitMinutes;

    /** 首时段价格 */
    private BigDecimal firstPeriodPrice;

    /** 后续单价 */
    private BigDecimal subsequentPrice;

    /** 24小时封顶金额（NULL 表示不封顶） */
    private BigDecimal dailyCap;

    /** 夜间封顶金额（NULL 表示不封顶） */
    private BigDecimal nightCap;

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
}
