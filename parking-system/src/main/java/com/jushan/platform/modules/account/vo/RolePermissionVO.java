package com.jushan.platform.modules.account.vo;

import java.time.LocalDateTime;

/**
 * 角色权限矩阵视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class RolePermissionVO {

    /** 矩阵项 ID */
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 权限编码 */
    private String permissionCode;

    /** 权限类型 */
    private String permissionType;

    /** 数据范围 */
    private String dataScope;

    /** 创建时间 */
    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }

    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
