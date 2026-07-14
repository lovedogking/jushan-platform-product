package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 绑定手机号请求 DTO。
 * <p>
 * 支持手机号验证码绑定。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BindPhoneRequest {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 验证码（Mock 版本不验证） */
    private String verifyCode;

    // ==================== getter / setter ====================

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVerifyCode() { return verifyCode; }
    public void setVerifyCode(String verifyCode) { this.verifyCode = verifyCode; }
}