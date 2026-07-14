package com.jushan.platform.modules.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理员账号实体。
 * <p>
 * 支持三级管理员绑定：
 * <ul>
 *   <li>一级：平台用户，tenant_id 可为 NULL，管理全平台</li>
 *   <li>二级：公司管理员，company_id 必填</li>
 *   <li>三级：停车场管理员，company_id 和 lot_id 必填</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_account")
public class SysAdminAccount extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属公司 ID（二级、三级管理员必填） */
    private Long companyId;

    /** 所属停车场 ID（三级管理员必填） */
    private Long lotId;

    /** 登录账号 */
    private String username;

    /** BCrypt 密码哈希 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 管理员级别：1平台 2公司 3停车场 */
    private Integer level;

    /** 账号状态：0禁用 1正常 2锁定 */
    private Integer status;

    /** 连续登录失败次数 */
    private Integer loginFailCount;

    /** 锁定截止时间（NULL 表示未锁定） */
    private LocalDateTime lockUntil;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
}
