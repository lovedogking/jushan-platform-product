package com.jushan.platform.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户主表实体。
 * <p>
 * 系统级表，不受租户拦截器过滤。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户名称 */
    private String name;

    /** 租户编码（唯一） */
    private String code;

    /** 状态：1正常 0禁用 */
    private Integer status;
}
