package com.jushan.system.vo;

import java.util.Collections;
import java.util.List;

/**
 * 登录用户视图（含角色和权限）。
 * <p>
 * 对应前端类型 {@code LoginUser} 和 {@code UserInfo}。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class LoginUserVo {

    /** 用户 ID（字符串，兼容 JS Number 安全范围） */
    private String userId;

    /** 登录账号 */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 角色列表 */
    private List<String> roles;

    /** 权限列表（T13 细化，当前返回空列表） */
    private List<String> permissions;

    /** 租户 ID（平台用户为 null） */
    private Long tenantId;

    /** 代理状态（包含 isProxy 等字段） */
    private java.util.Map<String, Object> proxy;

    public LoginUserVo() {}

    public LoginUserVo(String userId, String username, String displayName, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.roles = roles;
        this.permissions = Collections.emptyList();
    }

    public LoginUserVo(String userId, String username, String displayName,
                       List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.roles = roles;
        this.permissions = permissions != null ? permissions : Collections.emptyList();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public java.util.Map<String, Object> getProxy() { return proxy; }
    public void setProxy(java.util.Map<String, Object> proxy) { this.proxy = proxy; }
}
