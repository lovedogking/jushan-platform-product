package com.jushan.platform.modules.parking.vo;

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
    /** 营收（一期返回 null） */
    private Object revenue;

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
    public Object getRevenue() { return revenue; }
    public void setRevenue(Object revenue) { this.revenue = revenue; }
}
