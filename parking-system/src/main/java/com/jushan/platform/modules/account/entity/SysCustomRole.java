package com.jushan.platform.modules.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义角色实体。
 * <p>
 * 租户维度下的角色定义，用于构建管理员权限矩阵。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_custom_role")
public class SysCustomRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色名称 */
    private String roleName;

    /** 角色编码（租户内唯一） */
    private String roleCode;

    /** 角色描述 */
    private String description;
}
