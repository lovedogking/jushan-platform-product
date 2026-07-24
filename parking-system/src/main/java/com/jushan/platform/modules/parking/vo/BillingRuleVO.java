package com.jushan.platform.modules.parking.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收费规则视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BillingRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long parkingLotId;
    private String parkingLotName;
    private String name;
    private String description;
    private String ruleType;
    private String ruleTypeDesc;
    private String status;
    private String statusDesc;
    private Integer isDefault;
    private String effectType;
    private String effectTypeDesc;
    private String effectTime;
    private Long createdBy;
    private String createdByName;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 当前生效版本信息
    private Long activeVersionId;
    private Integer activeVersion;
    private String configSummary;
    private Integer freeMinutes;
    private Integer firstPeriod;
    private Integer firstAmount;
    private Integer unitPeriod;
    private Integer unitAmount;
    private Integer dailyCap;
    private Integer maxAmount;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getRuleTypeDesc() { return ruleTypeDesc; }
    public void setRuleTypeDesc(String ruleTypeDesc) { this.ruleTypeDesc = ruleTypeDesc; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }

    public String getEffectType() { return effectType; }
    public void setEffectType(String effectType) { this.effectType = effectType; }

    public String getEffectTypeDesc() { return effectTypeDesc; }
    public void setEffectTypeDesc(String effectTypeDesc) { this.effectTypeDesc = effectTypeDesc; }

    public String getEffectTime() { return effectTime; }
    public void setEffectTime(String effectTime) { this.effectTime = effectTime; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getActiveVersionId() { return activeVersionId; }
    public void setActiveVersionId(Long activeVersionId) { this.activeVersionId = activeVersionId; }

    public Integer getActiveVersion() { return activeVersion; }
    public void setActiveVersion(Integer activeVersion) { this.activeVersion = activeVersion; }

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
}