package com.jushan.platform.modules.parking.vo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AnalyticsOverviewVO {
    /** 入场总数 */
    private Long entryCount;
    /** 出场总数 */
    private Long exitCount;
    /** 当前在场车辆数 */
    private Long currentInCount;
    /** 入场触发方式统计: whitelist_auto / manual_open / always_open_period / manual_entry */
    private Map<String, Long> entryTriggerStats;
    /** 车流量趋势数据（按时段聚合） */
    private List<TrendPoint> trendData;
    /** 营收汇总（单位：元） */
    private RevenueStats revenue;

    public static class RevenueStats {
        /** 实收总额（元）：统计周期内已出场会话 paid_amount 汇总 */
        private BigDecimal totalPaid;
        /** 固定车营收（元）：一期无固定车收费数据源，固定为 0 */
        private BigDecimal fixedCarRevenue;
        /** 其他营收（元）= totalPaid - fixedCarRevenue */
        private BigDecimal otherRevenue;

        public RevenueStats() {}
        public RevenueStats(BigDecimal totalPaid, BigDecimal fixedCarRevenue, BigDecimal otherRevenue) {
            this.totalPaid = totalPaid;
            this.fixedCarRevenue = fixedCarRevenue;
            this.otherRevenue = otherRevenue;
        }
        public BigDecimal getTotalPaid() { return totalPaid; }
        public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
        public BigDecimal getFixedCarRevenue() { return fixedCarRevenue; }
        public void setFixedCarRevenue(BigDecimal fixedCarRevenue) { this.fixedCarRevenue = fixedCarRevenue; }
        public BigDecimal getOtherRevenue() { return otherRevenue; }
        public void setOtherRevenue(BigDecimal otherRevenue) { this.otherRevenue = otherRevenue; }
    }

    public static class TrendPoint {
        private String time;
        private Long entry;
        private Long exit;

        public TrendPoint() {}
        public TrendPoint(String time, Long entry, Long exit) {
            this.time = time; this.entry = entry; this.exit = exit;
        }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public Long getEntry() { return entry; }
        public void setEntry(Long entry) { this.entry = entry; }
        public Long getExit() { return exit; }
        public void setExit(Long exit) { this.exit = exit; }
    }

    public Long getEntryCount() { return entryCount; }
    public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
    public Long getExitCount() { return exitCount; }
    public void setExitCount(Long exitCount) { this.exitCount = exitCount; }
    public Long getCurrentInCount() { return currentInCount; }
    public void setCurrentInCount(Long currentInCount) { this.currentInCount = currentInCount; }
    public Map<String, Long> getEntryTriggerStats() { return entryTriggerStats; }
    public void setEntryTriggerStats(Map<String, Long> entryTriggerStats) { this.entryTriggerStats = entryTriggerStats; }
    public List<TrendPoint> getTrendData() { return trendData; }
    public void setTrendData(List<TrendPoint> trendData) { this.trendData = trendData; }
    public RevenueStats getRevenue() { return revenue; }
    public void setRevenue(RevenueStats revenue) { this.revenue = revenue; }
}
