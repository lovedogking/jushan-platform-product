package com.jushan.platform.modules.parking.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 分时段计费配置对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeSegmentConfig {

    /** 时段开始时间，格式 HH:mm */
    private String startTime;

    /** 时段结束时间，格式 HH:mm */
    private String endTime;

    /** 该时段内后续单位时长（分钟），为空则继承基础配置 */
    private Integer unitPeriod;

    /** 该时段内后续单位金额（分），为空则继承基础配置 */
    private Integer unitAmount;

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Integer getUnitPeriod() {
        return unitPeriod;
    }

    public void setUnitPeriod(Integer unitPeriod) {
        this.unitPeriod = unitPeriod;
    }

    public Integer getUnitAmount() {
        return unitAmount;
    }

    public void setUnitAmount(Integer unitAmount) {
        this.unitAmount = unitAmount;
    }
}
