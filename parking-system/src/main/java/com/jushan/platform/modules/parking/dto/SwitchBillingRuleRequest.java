package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 切换收费规则请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class SwitchBillingRuleRequest {

    /** 切换后的目标规则 ID */
    @NotNull(message = "目标规则 ID 不能为空")
    private Long targetRuleId;

    /** 是否影响已在场车辆：true-是, false-否（仅对新入场生效） */
    private Boolean applyToExisting = false;

    /** 切换原因 */
    private String reason;

    // ==================== getter / setter ====================

    public Long getTargetRuleId() { return targetRuleId; }
    public void setTargetRuleId(Long targetRuleId) { this.targetRuleId = targetRuleId; }

    public Boolean getApplyToExisting() { return applyToExisting; }
    public void setApplyToExisting(Boolean applyToExisting) { this.applyToExisting = applyToExisting; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}