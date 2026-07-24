package com.jushan.platform.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 租户审核请求参数。
 * <p>
 * 超级管理员或平台运营审核客户注册申请（通过或拒绝）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class TenantAuditRequest {

    /** 操作类型：APPROVED / REJECTED / ENABLED / DISABLED */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /** 操作原因/备注（拒绝时必须填写） */
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String reason;

    // ==================== getter / setter ====================

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
