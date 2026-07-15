package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 续费订单视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class RenewalOrderVO {

    /** 续费订单 ID */
    private Long orderId;

    /** 续费订单号 */
    private String orderNo;

    /** 续费金额（分） */
    private Integer amountCents;

    /** 订单状态 */
    private String status;

    /** 关联车辆 ID */
    private Long vehicleId;

    /** 续费月数 */
    private Integer renewalMonths;

    /** 车牌号 */
    private String plateNumber;

    /** 续费后有效期结束日（生效回调后填充） */
    private LocalDate newValidEndDate;
}
