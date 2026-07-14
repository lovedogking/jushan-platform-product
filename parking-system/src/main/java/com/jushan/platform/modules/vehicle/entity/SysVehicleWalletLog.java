package com.jushan.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 储值车钱包流水实体。
 * <p>
 * 记录充值、消费、退款、调账等流水。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_vehicle_wallet_log")
public class SysVehicleWalletLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 车辆ID */
    private Long vehicleId;

    /** 钱包ID */
    private Long walletId;

    /** 流水类型：RECHARGE-充值, CONSUME-消费, REFUND-退款, ADJUST-调账 */
    private String logType;

    /** 变动金额（元，正数增加，负数减少） */
    private BigDecimal amount;

    /** 变动前余额（元） */
    private BigDecimal balanceBefore;

    /** 变动后余额（元） */
    private BigDecimal balanceAfter;

    /** 关联订单ID */
    private Long orderId;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 备注 */
    private String remark;

    // ==================== 常量定义 ====================

    public static final String TYPE_RECHARGE = "RECHARGE";
    public static final String TYPE_CONSUME = "CONSUME";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_ADJUST = "ADJUST";
}
