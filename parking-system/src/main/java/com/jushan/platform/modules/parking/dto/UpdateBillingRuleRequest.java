package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 更新收费规则请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class UpdateBillingRuleRequest {

    /** 规则名称 */
    @Size(max = 100, message = "规则名称最多 100 字符")
    private String name;

    /** 规则描述 */
    @Size(max = 500, message = "规则描述最多 500 字符")
    private String description;

    /** 规则类型：HOURLY-按时长, FIXED-固定金额, NO_FEE-免费 */
    private String ruleType;

    /** 状态：ENABLED-启用, DISABLED-禁用 */
    private String status;

    /** 生效方式：IMMEDIATE-立即生效, NEW_ENTRY_ONLY-仅新入场生效, SCHEDULED-定时生效 */
    private String effectType;

    /** 定时生效时间（effect_type=SCHEDULED 时必填） */
    private String effectTime;

    // 计费配置字段
    /** 免费时长（分钟） */
    @Min(value = 0, message = "免费时长不能为负数")
    private Integer freeMinutes;

    /** 首时段时长（分钟） */
    @Min(value = 0, message = "首时段时长不能为负数")
    private Integer firstPeriod;

    /** 首时段金额（分） */
    @Min(value = 0, message = "首时段金额不能为负数")
    private Integer firstAmount;

    /** 续费单位时长（分钟） */
    @Min(value = 0, message = "续费单位时长不能为负数")
    private Integer unitPeriod;

    /** 续费单位金额（分） */
    @Min(value = 0, message = "续费单位金额不能为负数")
    private Integer unitAmount;

    /** 单日封顶金额（分），0表示不封顶 */
    @Min(value = 0, message = "单日封顶金额不能为负数")
    private Integer dailyCap;

    /** 最大金额（分），0表示不封顶 */
    @Min(value = 0, message = "最大金额不能为负数")
    private Integer maxAmount;

    // ==================== getter / setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEffectType() { return effectType; }
    public void setEffectType(String effectType) { this.effectType = effectType; }

    public String getEffectTime() { return effectTime; }
    public void setEffectTime(String effectTime) { this.effectTime = effectTime; }

    public Integer getFreeMinutes() { return freeMinutes; }
    public void setFreeMinutes(Integer freeMinutes) { this.freeMinutes = freeMinutes; }

    public Integer getFirstPeriod() { return firstPeriod; }
    public void setFirstPeriod(Integer firstPeriod) { this.firstPeriod = firstPeriod; }

    public Integer getFirstAmount() { return firstAmount; }
    public void setFirstAmount(Integer firstAmount) { this.firstAmount = firstAmount; }

    public Integer getUnitPeriod() { return unitPeriod; }
    public void setUnitPeriod(Integer unitPeriod) { this.unitPeriod = unitPeriod; }

    public Integer getUnitAmount() { return unitAmount; }
    public void setUnitAmount(Integer unitAmount) { this.unitAmount = unitAmount; }

    public Integer getDailyCap() { return dailyCap; }
    public void setDailyCap(Integer dailyCap) { this.dailyCap = dailyCap; }

    public Integer getMaxAmount() { return maxAmount; }
    public void setMaxAmount(Integer maxAmount) { this.maxAmount = maxAmount; }
}