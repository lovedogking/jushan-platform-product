package com.jushan.platform.modules.auth.vo;

import java.util.List;

/**
 * 登录结果。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class LoginResult {

    /** Access Token */
    private String token;

    /** Refresh Token */
    private String refreshToken;

    /** 用户信息 */
    private UserInfo userInfo;

    /** 权限编码列表 */
    private List<String> permissions;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    /**
     * 用户信息视图。
     */
    public static class UserInfo {

        /** 用户ID */
        private Long userId;

        /** 登录账号 */
        private String username;

        /** 真实姓名 */
        private String realName;

        /** 管理员级别：1平台 2公司 3停车场 */
        private Integer level;

        /** 租户ID（平台用户为 null） */
        private Long tenantId;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }
    }
}
