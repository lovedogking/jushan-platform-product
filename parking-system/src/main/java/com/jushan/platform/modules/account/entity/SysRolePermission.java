package com.jushan.platform.modules.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色权限矩阵实体。
 * <p>
 * 记录角色与权限码的映射关系，支持权限类型与数据范围扩展。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色 ID */
    private Long roleId;

    /** 权限编码 */
    private String permissionCode;

    /** 权限类型 */
    private String permissionType;

    /** 数据范围 */
    private String dataScope;
}
