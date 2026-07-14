package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 解绑车牌请求 DTO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class UnbindPlateRequest {

    /** 绑定记录 ID */
    @NotNull(message = "绑定记录 ID 不能为空")
    private Long bindingId;

    /** 解绑原因（可选） */
    private String reason;

    // ==================== getter / setter ====================

    public Long getBindingId() { return bindingId; }
    public void setBindingId(Long bindingId) { this.bindingId = bindingId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}