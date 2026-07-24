package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class MiniMonthlyPassRenewRequest {

    @Min(value = 1, message = "续费月数至少为1")
    @Max(value = 12, message = "续费月数最多为12")
    private int months;

    public int getMonths() { return months; }
    public void setMonths(int months) { this.months = months; }
}
