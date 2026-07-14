package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信登录请求 DTO。
 * <p>
 * 微信小程序通过 wx.login() 获取 code，后端使用 code 换取 openid。
 * 真实微信登录需要调用微信接口；本版本提供 Mock 登录用于开发测试。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class WxLoginRequest {

    /** 微信登录凭证 code（通过 wx.login() 获取） */
    @NotBlank(message = "登录凭证不能为空")
    private String code;

    /** 昵称（可选，微信授权获取） */
    private String nickname;

    /** 头像 URL（可选，微信授权获取） */
    private String avatarUrl;

    // ==================== getter / setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}