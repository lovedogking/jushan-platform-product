package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收费规则主表实体。
 * <p>
 * T34｜收费规则与版本 CRUD
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("billing_rule")
public class BillingRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属停车场 ID */
    private Long parkingLotId;

    /** 规则名称 */
    private String name;

    /** 规则描述 */
    private String description;

    /** 规则类型：HOURLY-按时长, FIXED-固定金额, NO_FEE-免费 */
    private String ruleType;

    /** 状态：ENABLED-启用, DISABLED-禁用 */
    private String status;

    /** 是否为默认规则：1-是, 0-否 */
    private Integer isDefault;

    /** 生效方式：IMMEDIATE-立即生效, NEW_ENTRY_ONLY-仅新入场生效, SCHEDULED-定时生效 */
    private String effectType;

    /** 定时生效时间（effect_type=SCHEDULED 时必填） */
    private LocalDateTime effectTime;

    /** 创建人 ID */
    private Long createdBy;

    /** 修改人 ID */
    private Long updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== 常量定义 ====================

    /** 规则类型 */
    public static final String RULE_TYPE_HOURLY = "HOURLY";
    public static final String RULE_TYPE_FIXED = "FIXED";
    public static final String RULE_TYPE_NO_FEE = "NO_FEE";

    /** 状态 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 生效方式 */
    public static final String EFFECT_IMMEDIATE = "IMMEDIATE";
    public static final String EFFECT_NEW_ENTRY_ONLY = "NEW_ENTRY_ONLY";
    public static final String EFFECT_SCHEDULED = "SCHEDULED";

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }

    public String getEffectType() { return effectType; }
    public void setEffectType(String effectType) { this.effectType = effectType; }

    public LocalDateTime getEffectTime() { return effectTime; }
    public void setEffectTime(LocalDateTime effectTime) { this.effectTime = effectTime; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}