package com.jushan.platform.modules.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 保存角色权限矩阵请求参数。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class RolePermissionSaveCmd {

    /** 权限矩阵项列表 */
    @NotEmpty(message = "权限矩阵不能为空")
    @Valid
    private List<Item> permissions;

    /**
     * 权限矩阵项。
     */
    public static class Item {

        /** 权限编码 */
        @NotBlank(message = "权限编码不能为空")
        @Size(max = 128, message = "权限编码长度不能超过128个字符")
        private String permissionCode;

        /** 权限类型 */
        @Size(max = 64, message = "权限类型长度不能超过64个字符")
        private String permissionType;

        /** 数据范围 */
        @Size(max = 64, message = "数据范围长度不能超过64个字符")
        private String dataScope;

        // ==================== getter / setter ====================

        public String getPermissionCode() { return permissionCode; }
        public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

        public String getPermissionType() { return permissionType; }
        public void setPermissionType(String permissionType) { this.permissionType = permissionType; }

        public String getDataScope() { return dataScope; }
        public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    }

    // ==================== getter / setter ====================

    public List<Item> getPermissions() { return permissions; }
    public void setPermissions(List<Item> permissions) { this.permissions = permissions; }
}
