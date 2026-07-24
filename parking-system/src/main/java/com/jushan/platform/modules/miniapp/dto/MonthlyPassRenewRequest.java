package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 月卡续期请求 DTO（Phase 1 A1）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MonthlyPassRenewRequest {

    @Min(value = 1, message = "续费月数必须大于0")
    @NotNull(message = "续费月数不能为空")
    private Integer renewalMonths;

    @NotNull(message = "金额不能为空")
    private Integer amountCents;

    private String remark;

    public Integer getRenewalMonths() { return renewalMonths; }
    public void setRenewalMonths(Integer renewalMonths) { this.renewalMonths = renewalMonths; }
    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
