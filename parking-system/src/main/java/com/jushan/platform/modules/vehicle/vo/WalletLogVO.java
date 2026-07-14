package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class WalletLogVO {

    /** 流水ID */
    private Long id;

    /** 车辆ID */
    private Long vehicleId;

    /** 钱包ID */
    private Long walletId;

    /** 流水类型：RECHARGE-充值, CONSUME-消费, REFUND-退款, ADJUST-调账 */
    private String logType;

    /** 变动金额（元） */
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

    /** 创建时间 */
    private LocalDateTime createdAt;
}
