package com.jushan.system.dto;

import com.jushan.system.vo.LoginUserVo;

/**
 * 登录成功返回。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class LoginResult {

    /** Sa-Token 令牌值 */
    private String accessToken;

    /** 令牌类型 */
    private String tokenType;

    /** 过期秒数 */
    private long expiresInSeconds;

    /** 登录用户信息 */
    private LoginUserVo user;

    /** 凭据状态：ACTIVE-正常, EXPIRED-需强制修改密码（FIX-04） */
    private String credentialStatus;

    public LoginResult() {}

    public LoginResult(String accessToken, long expiresInSeconds, LoginUserVo user) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.expiresInSeconds = expiresInSeconds;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(long expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }

    public LoginUserVo getUser() { return user; }
    public void setUser(LoginUserVo user) { this.user = user; }

    public String getCredentialStatus() { return credentialStatus; }
    public void setCredentialStatus(String credentialStatus) { this.credentialStatus = credentialStatus; }
}
