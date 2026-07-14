package com.jushan.platform.modules.account.vo;

/**
 * 重置密码响应对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ResetPasswordVO {

    /** 明文密码（仅本次返回，请提醒用户及时修改） */
    private String plainPassword;

    public ResetPasswordVO() {}

    public ResetPasswordVO(String plainPassword) {
        this.plainPassword = plainPassword;
    }

    // ==================== getter / setter ====================

    public String getPlainPassword() { return plainPassword; }
    public void setPlainPassword(String plainPassword) { this.plainPassword = plainPassword; }
}
