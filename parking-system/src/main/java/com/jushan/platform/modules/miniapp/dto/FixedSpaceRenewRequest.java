package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 固定车位续期请求 DTO（Phase 1 A2）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class FixedSpaceRenewRequest {

    @NotNull(message = "新有效期止不能为空")
    @Future(message = "新有效期止必须是将来的日期")
    private LocalDate newValidEnd;

    /** 实收金额（分） */
    @NotNull(message = "实收金额不能为空")
    private Integer paidAmountCents;

    /** 缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER */
    @NotBlank(message = "缴费方式不能为空")
    private String payMethod;

    private String remark;

    public LocalDate getNewValidEnd() { return newValidEnd; }
    public void setNewValidEnd(LocalDate newValidEnd) { this.newValidEnd = newValidEnd; }
    public Integer getPaidAmountCents() { return paidAmountCents; }
    public void setPaidAmountCents(Integer paidAmountCents) { this.paidAmountCents = paidAmountCents; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
