package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用计算结果视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeCalculateResultVO {

    private Long lotId;
    private Long zoneId;
    private String plateNumber;
    private String vehicleType;
    /** 停车时长（分钟） */
    private Integer parkingDuration;
    /** 免费时长（分钟） */
    private Integer freeMinutes;
    /** 计费时长（分钟） */
    private Integer billingDuration;
    /** 原始应收金额（元） */
    private BigDecimal originalAmount;
    /** 优惠金额（元） */
    private BigDecimal discountAmount;
    /** 应付金额（元，分位四舍五入） */
    private BigDecimal payableAmount;
    /** 匹配到的收费规则 ID */
    private Long feeRuleId;
    /** 匹配到的收费规则名称 */
    private String feeRuleName;
    /** 费用明细 */
    private List<BreakdownItem> breakdown;

    @Data
    public static class BreakdownItem {
        /** 费用项名称 */
        private String itemName;
        /** 时长（分钟） */
        private Integer duration;
        /** 计费单位数 */
        private Integer unitCount;
        /** 金额（元） */
        private BigDecimal amount;

        public BreakdownItem() {}

        public BreakdownItem(String itemName, Integer duration, Integer unitCount, BigDecimal amount) {
            this.itemName = itemName;
            this.duration = duration;
            this.unitCount = unitCount;
            this.amount = amount;
        }
    }
}
