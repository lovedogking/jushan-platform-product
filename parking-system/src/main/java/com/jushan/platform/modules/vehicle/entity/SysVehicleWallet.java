package com.jushan.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 储值车钱包实体。
 * <p>
 * 与车辆主表一对一关联，使用 version 乐观锁字段。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_vehicle_wallet")
public class SysVehicleWallet extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 车辆ID */
    private Long vehicleId;

    /** 当前余额（元） */
    private BigDecimal balance;

    /** 累计充值金额（元） */
    private BigDecimal totalRecharge;

    /** 累计消费金额（元） */
    private BigDecimal totalConsume;

    /** 乐观锁版本号 */
    private Integer version;
}
