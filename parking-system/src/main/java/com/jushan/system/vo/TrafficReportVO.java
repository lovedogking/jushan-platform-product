package com.jushan.system.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 车流量报表视图（包 6-1）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class TrafficReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总入场量 */
    private Long totalEntry;

    /** 总出场量 */
    private Long totalExit;

    /** 峰值小时 (0-23) */
    private Integer peakHour;

    /** 峰值小时车流量 */
    private Long peakCount;

    /** 按天汇总 */
    private List<DailyStat> dailyStats;

    /** 按小时汇总（跨天合并同小时） */
    private List<HourlyStat> hourlyStats;

    // getter / setter
    public Long getTotalEntry() { return totalEntry; }
    public void setTotalEntry(Long totalEntry) { this.totalEntry = totalEntry; }

    public Long getTotalExit() { return totalExit; }
    public void setTotalExit(Long totalExit) { this.totalExit = totalExit; }

    public Integer getPeakHour() { return peakHour; }
    public void setPeakHour(Integer peakHour) { this.peakHour = peakHour; }

    public Long getPeakCount() { return peakCount; }
    public void setPeakCount(Long peakCount) { this.peakCount = peakCount; }

    public List<DailyStat> getDailyStats() { return dailyStats; }
    public void setDailyStats(List<DailyStat> dailyStats) { this.dailyStats = dailyStats; }

    public List<HourlyStat> getHourlyStats() { return hourlyStats; }
    public void setHourlyStats(List<HourlyStat> hourlyStats) { this.hourlyStats = hourlyStats; }

    public static class DailyStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private String date;
        private Long entryCount;
        private Long exitCount;

        public DailyStat() {}
        public DailyStat(String date, Long entryCount, Long exitCount) {
            this.date = date;
            this.entryCount = entryCount;
            this.exitCount = exitCount;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public Long getEntryCount() { return entryCount; }
        public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
        public Long getExitCount() { return exitCount; }
        public void setExitCount(Long exitCount) { this.exitCount = exitCount; }
    }

    public static class HourlyStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private int hour;
        private Long entryCount;
        private Long exitCount;
        private Long total;

        public HourlyStat() {}
        public HourlyStat(int hour, Long entryCount, Long exitCount) {
            this.hour = hour;
            this.entryCount = entryCount;
            this.exitCount = exitCount;
            this.total = (entryCount != null ? entryCount : 0) + (exitCount != null ? exitCount : 0);
        }

        public int getHour() { return hour; }
        public void setHour(int hour) { this.hour = hour; }
        public Long getEntryCount() { return entryCount; }
        public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
        public Long getExitCount() { return exitCount; }
        public void setExitCount(Long exitCount) { this.exitCount = exitCount; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
    }
}
