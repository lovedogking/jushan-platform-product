package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收费规则切换审计日志实体。
 * <p>
 * T34｜收费规则与版本 CRUD
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("billing_rule_switch_log")
public class BillingRuleSwitchLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 租户 ID */
    private Long tenantId;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 切换前规则 ID */
    private Long beforeRuleId;

    /** 切换后规则 ID */
    private Long afterRuleId;

    /** 是否影响已在场车辆：1-是, 0-否 */
    private Integer applyToExisting;

    /** 切换原因 */
    private String reason;

    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public Long getBeforeRuleId() { return beforeRuleId; }
    public void setBeforeRuleId(Long beforeRuleId) { this.beforeRuleId = beforeRuleId; }

    public Long getAfterRuleId() { return afterRuleId; }
    public void setAfterRuleId(Long afterRuleId) { this.afterRuleId = afterRuleId; }

    public Integer getApplyToExisting() { return applyToExisting; }
    public void setApplyToExisting(Integer applyToExisting) { this.applyToExisting = applyToExisting; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}