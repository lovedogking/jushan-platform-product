package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收费规则版本实体（快照存储）。
 * <p>
 * T34｜收费规则与版本 CRUD
 * 版本不可修改，每次变更创建新版本。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("billing_rule_version")
public class BillingRuleVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属规则 ID */
    private Long ruleId;

    /** 所属租户 ID（冗余字段） */
    private Long tenantId;

    /** 所属停车场 ID（冗余字段） */
    private Long parkingLotId;

    /** 版本号（递增） */
    private Integer version;

    /** 是否为当前生效版本：1-是, 0-否 */
    private Integer isActive;

    /** 计费配置 JSON */
    private String config;

    /** 计费配置摘要 */
    private String configSummary;

    /** 免费时长（分钟） */
    private Integer freeMinutes;

    /** 首时段时长（分钟） */
    private Integer firstPeriod;

    /** 首时段金额（分） */
    private Integer firstAmount;

    /** 续费单位时长（分钟） */
    private Integer unitPeriod;

    /** 续费单位金额（分） */
    private Integer unitAmount;

    /** 单日封顶金额（分），0表示不封顶 */
    private Integer dailyCap;

    /** 最大金额（分），0表示不封顶 */
    private Integer maxAmount;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 创建人 ID */
    private Long createdBy;

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}