package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 收费规则视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeRuleVO {

    private Long id;
    private Long tenantId;
    private Long lotId;
    private Long zoneId;
    private String name;
    /** 计费模式：1按时 2按次 3阶梯 4分时段 */
    private Integer billingMode;
    private Integer freeMinutes;
    private Integer unitMinutes;
    /** 首时段时长（分钟） */
    private Integer firstPeriodMinutes;
    private BigDecimal firstPeriodPrice;
    private BigDecimal subsequentPrice;
    private BigDecimal dailyCap;
    /** 最大封顶金额（整单封顶） */
    private BigDecimal maxAmount;
    private BigDecimal nightCap;
    /** 跨天计费规则：1按自然日分段 2连续计费 */
    private Integer crossDayMode;
    /** 生效方式：1立即生效 2仅新入场生效 3定时生效 */
    private Integer effectMode;
    private Integer priority;
    /** 状态：1启用 2禁用 */
    private Integer status;
    private LocalDateTime effectiveStart;
    private LocalDateTime effectiveEnd;
    private String holidayRules;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 时段列表（分时段模式下返回） */
    private List<FeeRuleSegmentVO> timeSegments;
}
