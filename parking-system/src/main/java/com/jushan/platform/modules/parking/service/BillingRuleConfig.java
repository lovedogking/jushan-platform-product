package com.jushan.platform.modules.parking.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 收费规则配置对象（从 billing_rule_version.config JSON 反序列化）。
 * <p>
 * 金额统一使用整数分，禁止浮点数。
 * 所有字段均为可选，缺失时按 0 或默认值处理；冲突/非法配置由计费引擎失败关闭。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillingRuleConfig {

    /** 免费时长（分钟） */
    private Integer freeMinutes;

    /** 首时段时长（分钟） */
    private Integer firstPeriod;

    /** 首时段金额（分） */
    private Integer firstAmount;

    /** 后续单位时长（分钟），默认 60 */
    private Integer unitPeriod;

    /** 后续单位金额（分） */
    private Integer unitAmount;

    /** 单日封顶金额（分），0 表示不封顶 */
    private Integer dailyCap;

    /** 最大封顶金额（分），0 表示不封顶 */
    private Integer maxAmount;

    /** 固定金额规则专用金额（分），优先级高于 firstAmount */
    private Integer fixedAmount;

    /** 分时段计费规则 */
    private List<TimeSegmentConfig> timeSegments;

    public Integer getFreeMinutes() {
        return freeMinutes;
    }

    public void setFreeMinutes(Integer freeMinutes) {
        this.freeMinutes = freeMinutes;
    }

    public Integer getFirstPeriod() {
        return firstPeriod;
    }

    public void setFirstPeriod(Integer firstPeriod) {
        this.firstPeriod = firstPeriod;
    }

    public Integer getFirstAmount() {
        return firstAmount;
    }

    public void setFirstAmount(Integer firstAmount) {
        this.firstAmount = firstAmount;
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

    public Integer getDailyCap() {
        return dailyCap;
    }

    public void setDailyCap(Integer dailyCap) {
        this.dailyCap = dailyCap;
    }

    public Integer getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(Integer maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Integer getFixedAmount() {
        return fixedAmount;
    }

    public void setFixedAmount(Integer fixedAmount) {
        this.fixedAmount = fixedAmount;
    }

    public List<TimeSegmentConfig> getTimeSegments() {
        return timeSegments;
    }

    public void setTimeSegments(List<TimeSegmentConfig> timeSegments) {
        this.timeSegments = timeSegments;
    }
}
