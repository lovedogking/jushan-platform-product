package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 收费规则时段实体（辅助表）。
 * <p>
 * 仅分时段计费模式（billing_mode = 4）下使用。
 * 同一规则下时段不重叠，由业务层校验。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fee_rule_segment")
public class FeeRuleSegment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 收费规则 ID（逻辑外键：fee_rule.id） */
    private Long feeRuleId;

    /** 时段名称，如白天/夜间 */
    private String segmentName;

    /** 时段开始时间 */
    private LocalTime startTime;

    /** 时段结束时间 */
    private LocalTime endTime;

    /** 计费单位（分钟） */
    private Integer unitMinutes;

    /** 时段单价 */
    private BigDecimal unitPrice;

    /** 时段封顶金额（NULL 表示不封顶） */
    private BigDecimal capAmount;

    /** 排序 */
    private Integer sortOrder;
}
