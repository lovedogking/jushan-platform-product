package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 停车场状态变更请求（启用/停用）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingLotStatusRequest {

    /** 操作类型：ENABLED / DISABLED */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /** 操作原因 */
    @Size(max = 255, message = "原因最长255个字符")
    private String reason;

    // ==================== getter / setter ====================

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

}
