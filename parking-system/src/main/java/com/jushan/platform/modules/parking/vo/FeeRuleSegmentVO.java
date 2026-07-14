package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 收费规则时段视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeRuleSegmentVO {

    private Long id;
    private Long feeRuleId;
    private String segmentName;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer unitMinutes;
    private BigDecimal unitPrice;
    private BigDecimal capAmount;
    private Integer sortOrder;
}
