package com.jushan.system.dto;

import jakarta.validation.constraints.Future;
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

    private String remark;

    public LocalDate getNewValidEnd() { return newValidEnd; }
    public void setNewValidEnd(LocalDate newValidEnd) { this.newValidEnd = newValidEnd; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
