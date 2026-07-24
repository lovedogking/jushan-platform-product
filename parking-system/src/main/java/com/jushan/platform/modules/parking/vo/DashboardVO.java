package com.jushan.platform.modules.parking.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 仪表盘首页数据 VO（Phase 2 D4）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 今日收入（分） */
    private Integer todayRevenue;

    /** 今日车流量汇总 */
    private TodayTraffic todayTraffic;

    /** 当前在场车辆数 */
    private Integer currentParkedCount;

    /** 当前余位数 */
    private Integer remainingSpaces;

    /** 设备在线/离线汇总 */
    private DeviceStatusSummary deviceStatus;

    /** 未处理异常告警数量 */
    private Integer unhandledAlertCount;

    /** 今日收入趋势（按小时） */
    private List<HourlyStat> hourlyRevenue;

    /** 今日车流量趋势（按小时） */
    private List<HourlyTrafficStat> hourlyTraffic;

    // ==================== getter / setter ====================

    public Integer getTodayRevenue() { return todayRevenue; }
    public void setTodayRevenue(Integer todayRevenue) { this.todayRevenue = todayRevenue; }

    public TodayTraffic getTodayTraffic() { return todayTraffic; }
    public void setTodayTraffic(TodayTraffic todayTraffic) { this.todayTraffic = todayTraffic; }

    public Integer getCurrentParkedCount() { return currentParkedCount; }
    public void setCurrentParkedCount(Integer currentParkedCount) { this.currentParkedCount = currentParkedCount; }

    public Integer getRemainingSpaces() { return remainingSpaces; }
    public void setRemainingSpaces(Integer remainingSpaces) { this.remainingSpaces = remainingSpaces; }

    public DeviceStatusSummary getDeviceStatus() { return deviceStatus; }
    public void setDeviceStatus(DeviceStatusSummary deviceStatus) { this.deviceStatus = deviceStatus; }

    public Integer getUnhandledAlertCount() { return unhandledAlertCount; }
    public void setUnhandledAlertCount(Integer unhandledAlertCount) { this.unhandledAlertCount = unhandledAlertCount; }

    public List<HourlyStat> getHourlyRevenue() { return hourlyRevenue; }
    public void setHourlyRevenue(List<HourlyStat> hourlyRevenue) { this.hourlyRevenue = hourlyRevenue; }

    public List<HourlyTrafficStat> getHourlyTraffic() { return hourlyTraffic; }
    public void setHourlyTraffic(List<HourlyTrafficStat> hourlyTraffic) { this.hourlyTraffic = hourlyTraffic; }

    // ==================== 内部类 ====================

    /** 今日车流量汇总 */
    public static class TodayTraffic implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer entryCount;
        private Integer exitCount;
        private Integer total;

        public TodayTraffic() {}

        public TodayTraffic(Integer entryCount, Integer exitCount, Integer total) {
            this.entryCount = entryCount;
            this.exitCount = exitCount;
            this.total = total;
        }

        public Integer getEntryCount() { return entryCount; }
        public void setEntryCount(Integer entryCount) { this.entryCount = entryCount; }

        public Integer getExitCount() { return exitCount; }
        public void setExitCount(Integer exitCount) { this.exitCount = exitCount; }

        public Integer getTotal() { return total; }
        public void setTotal(Integer total) { this.total = total; }
    }

    /** 设备在线/离线汇总 */
    public static class DeviceStatusSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer online;
        private Integer offline;
        private Integer total;

        public DeviceStatusSummary() {}

        public DeviceStatusSummary(Integer online, Integer offline, Integer total) {
            this.online = online;
            this.offline = offline;
            this.total = total;
        }

        public Integer getOnline() { return online; }
        public void setOnline(Integer online) { this.online = online; }

        public Integer getOffline() { return offline; }
        public void setOffline(Integer offline) { this.offline = offline; }

        public Integer getTotal() { return total; }
        public void setTotal(Integer total) { this.total = total; }
    }

    /** 按小时统计 */
    public static class HourlyStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer hour;
        private Integer amount;

        public HourlyStat() {}

        public HourlyStat(Integer hour, Integer amount) {
            this.hour = hour;
            this.amount = amount;
        }

        public Integer getHour() { return hour; }
        public void setHour(Integer hour) { this.hour = hour; }

        public Integer getAmount() { return amount; }
        public void setAmount(Integer amount) { this.amount = amount; }
    }

    /** 按小时车流量统计 */
    public static class HourlyTrafficStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer hour;
        private Integer entryCount;
        private Integer exitCount;

        public HourlyTrafficStat() {}

        public HourlyTrafficStat(Integer hour, Integer entryCount, Integer exitCount) {
            this.hour = hour;
            this.entryCount = entryCount;
            this.exitCount = exitCount;
        }

        public Integer getHour() { return hour; }
        public void setHour(Integer hour) { this.hour = hour; }

        public Integer getEntryCount() { return entryCount; }
        public void setEntryCount(Integer entryCount) { this.entryCount = entryCount; }

        public Integer getExitCount() { return exitCount; }
        public void setExitCount(Integer exitCount) { this.exitCount = exitCount; }
    }
}
