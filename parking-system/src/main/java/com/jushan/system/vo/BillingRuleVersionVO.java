package com.jushan.system.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收费规则版本视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BillingRuleVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long ruleId;
    private Long tenantId;
    private Long parkingLotId;
    private Integer version;
    private Integer isActive;
    private String config;
    private String configSummary;
    private Integer freeMinutes;
    private Integer firstPeriod;
    private Integer firstAmount;
    private Integer unitPeriod;
    private Integer unitAmount;
    private Integer dailyCap;
    private Integer maxAmount;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public String getConfigSummary() { return configSummary; }
    public void setConfigSummary(String configSummary) { this.configSummary = configSummary; }

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

    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}