package com.jushan.platform.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建自定义角色请求参数。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CustomRoleCreateCmd {

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过64个字符")
    private String roleName;

    /** 角色编码 */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过64个字符")
    private String roleCode;

    /** 角色描述 */
    @Size(max = 256, message = "角色描述长度不能超过256个字符")
    private String description;

    /** 租户 ID（平台用户创建平台角色时可为空） */
    private Long tenantId;

    // ==================== getter / setter ====================

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
