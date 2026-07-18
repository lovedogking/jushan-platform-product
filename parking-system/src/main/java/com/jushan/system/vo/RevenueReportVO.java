package com.jushan.system.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 收入报表视图（包 6-1）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class RevenueReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总收入（分），排除已退款 */
    private Long totalRevenue;

    /** 订单总数 */
    private Long orderCount;

    /** 平均订单金额（分） */
    private Long avgOrderAmount;

    /** 按期汇总的数据列表 */
    private List<PeriodStat> periods;

    // getter / setter
    public Long getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(Long totalRevenue) { this.totalRevenue = totalRevenue; }

    public Long getOrderCount() { return orderCount; }
    public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }

    public Long getAvgOrderAmount() { return avgOrderAmount; }
    public void setAvgOrderAmount(Long avgOrderAmount) { this.avgOrderAmount = avgOrderAmount; }

    public List<PeriodStat> getPeriods() { return periods; }
    public void setPeriods(List<PeriodStat> periods) { this.periods = periods; }

    public static class PeriodStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private String period;
        private Long totalRevenue;
        private Long orderCount;

        public PeriodStat() {}
        public PeriodStat(String period, Long totalRevenue, Long orderCount) {
            this.period = period;
            this.totalRevenue = totalRevenue;
            this.orderCount = orderCount;
        }

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public Long getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(Long totalRevenue) { this.totalRevenue = totalRevenue; }
        public Long getOrderCount() { return orderCount; }
        public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
    }
}
