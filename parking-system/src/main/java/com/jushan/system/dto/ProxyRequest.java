package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 代操作请求 DTO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ProxyRequest {

    /** 目标租户 ID */
    @NotNull(message = "目标租户 ID 不能为空")
    private Long tenantId;

    /** 操作原因/备注 */
    @NotBlank(message = "操作原因不能为空")
    private String reason;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
