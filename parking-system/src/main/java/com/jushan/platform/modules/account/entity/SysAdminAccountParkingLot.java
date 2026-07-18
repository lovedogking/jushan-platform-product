package com.jushan.platform.modules.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员账号与停车场关联实体（多对多）。
 * <p>
 * 用于岗亭管理员（level=3 + 岗亭角色）的多车场分配，
 * 替代旧 employee_parking_lot 表。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Data
@TableName("sys_admin_account_parking_lot")
public class SysAdminAccountParkingLot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 管理员账号 ID */
    private Long adminAccountId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
