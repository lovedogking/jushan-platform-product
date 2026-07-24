package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 小程序固定车位续费请求 DTO（任务包 5-2 固定车位部分）。
 * <p>
 * 对标 {@link MiniMonthlyPassRenewRequest}，仅需传入续费月数。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class MiniFixedSpaceRenewRequest {

    @Min(value = 1, message = "续费月数必须为 1/3/6/12")
    @Max(value = 12, message = "续费月数必须为 1/3/6/12")
    @NotNull(message = "续费月数不能为空")
    private Integer months;

    public Integer getMonths() { return months; }
    public void setMonths(Integer months) { this.months = months; }
}
