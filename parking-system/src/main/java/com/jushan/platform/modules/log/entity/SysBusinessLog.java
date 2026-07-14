package com.jushan.platform.modules.log.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务操作日志实体。
 * <p>
 * 对应表 {@code sys_business_log}，记录管理员对业务对象的操作行为、
 * 变更前后值以及操作结果，用于审计追溯。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_business_log")
public class SysBusinessLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名/账号 */
    private String operatorName;

    /** 操作IP */
    private String ip;

    /** 操作类型：CREATE/UPDATE/DELETE/LOGIN/RESET_PASSWORD 等 */
    private String operationType;

    /** 操作对象：Company/AdminAccount/AuthCode 等 */
    private String operationObject;

    /** 对象ID */
    private String objectId;

    /** 变更前完整值（JSON 字符串） */
    private String beforeValue;

    /** 变更后完整值（JSON 字符串） */
    private String afterValue;

    /** 操作结果：1成功 0失败 */
    private Integer result;

    /** 错误信息 */
    private String errorMsg;
}
