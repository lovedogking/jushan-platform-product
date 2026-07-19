package com.jushan.platform.modules.parking.dto;

import java.time.LocalDate;

public class AnalyticsQueryCmd {
    /** 停车场ID，0 或 null 表示全部 */
    private Long lotId;
    /** 时间周期：today / month / year / custom */
    private String period;
    /** 自定义起始日期（period=custom 时必填） */
    private LocalDate startDate;
    /** 自定义结束日期（period=custom 时必填） */
    private LocalDate endDate;

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
