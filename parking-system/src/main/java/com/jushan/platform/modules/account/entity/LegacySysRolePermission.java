package com.jushan.platform.modules.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 旧版角色-权限关联实体（Sa-Token 兼容遗留）。
 * <p>
 * 该实体仅用于保留旧 {@link com.jushan.system.service.PermissionService} 的编译兼容性，
 * 不再参与新的 Spring Security + JWT 权限体系。新体系使用
 * {@link com.jushan.platform.modules.account.entity.SysRolePermission}。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("sys_role_permission")
public class LegacySysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码 */
    private String roleCode;

    /** 权限编码 */
    private String permissionCode;

    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
