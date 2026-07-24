package com.jushan.platform.modules.miniapp.vo;

import java.time.LocalDateTime;

/**
 * 微信登录结果 VO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class WxLoginResult {

    /** 平台访问 Token */
    private String token;

    /** 用户 ID */
    private Long userId;

    /** 微信昵称 */
    private String nickname;

    /** 微信头像 */
    private String avatarUrl;

    /** 是否为新用户 */
    private Boolean isNewUser;

    /** 绑定车牌数量 */
    private Integer plateCount;

    /** 登录结果描述 */
    private String message;

    /** 手机号是否已绑定 */
    private Boolean phoneBound;

    /** 登录时间 */
    private LocalDateTime loginTime;

    // ==================== getter / setter ====================

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Boolean getIsNewUser() { return isNewUser; }
    public void setIsNewUser(Boolean isNewUser) { this.isNewUser = isNewUser; }

    public Integer getPlateCount() { return plateCount; }
    public void setPlateCount(Integer plateCount) { this.plateCount = plateCount; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Boolean getPhoneBound() { return phoneBound; }
    public void setPhoneBound(Boolean phoneBound) { this.phoneBound = phoneBound; }

    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }
}