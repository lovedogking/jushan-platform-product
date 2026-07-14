package com.jushan.platform.modules.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员账号角色关联实体。
 * <p>
 * 一个管理员账号可绑定一个或多个自定义角色。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_account_role")
public class SysAdminAccountRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 管理员账号 ID */
    private Long adminAccountId;

    /** 角色 ID */
    private Long roleId;
}
