package com.jushan.platform.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码请求参数。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ResetPasswordRequest {

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须在6-64个字符之间")
    private String newPassword;

    // ==================== getter / setter ====================

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
