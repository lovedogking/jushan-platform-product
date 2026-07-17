package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收费规则切换重新计算审计日志实体（P006）。
 * <p>
 * 当停车场切换收费规则并选择“影响已在场车辆”时，
 * 对当前在场停车记录按新规则重新计算费用，并记录本表供审计。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("billing_rule_recalc_log")
public class BillingRuleRecalcLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 停车记录 ID */
    private Long parkingRecordId;

    /** 车牌号 */
    private String plateNumber;

    /** 切换后的规则版本 ID */
    private Long ruleVersionId;

    /** 重新计算时的费用（分） */
    private Integer feeCents;

    /** 重新计算时间 */
    private LocalDateTime recalcTime;

    /** 操作人 ID */
    private Long operatorId;

    /** 原订单 ID（重算来源，EXIT_RESCAN 场景下与当前订单相同） */
    private Long originalOrderId;

    /** 原订单金额（分） */
    private Integer originalAmountCents;

    /** 重新计算金额（分） */
    private Integer newAmountCents;

    /** 重算触发原因：EXIT_RESCAN=出场重识别, TIMEOUT_RECALC=超时关单后重算 */
    private String triggerReason;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getParkingRecordId() { return parkingRecordId; }
    public void setParkingRecordId(Long parkingRecordId) { this.parkingRecordId = parkingRecordId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }

    public Integer getFeeCents() { return feeCents; }
    public void setFeeCents(Integer feeCents) { this.feeCents = feeCents; }

    public LocalDateTime getRecalcTime() { return recalcTime; }
    public void setRecalcTime(LocalDateTime recalcTime) { this.recalcTime = recalcTime; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public Long getOriginalOrderId() { return originalOrderId; }
    public void setOriginalOrderId(Long originalOrderId) { this.originalOrderId = originalOrderId; }

    public Integer getOriginalAmountCents() { return originalAmountCents; }
    public void setOriginalAmountCents(Integer originalAmountCents) { this.originalAmountCents = originalAmountCents; }

    public Integer getNewAmountCents() { return newAmountCents; }
    public void setNewAmountCents(Integer newAmountCents) { this.newAmountCents = newAmountCents; }

    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
