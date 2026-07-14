package com.jushan.platform.modules.miniapp.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 小程序停车记录视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class MiniParkingRecordVO {

    /** 记录ID */
    private Long id;

    /** 停车场名称 */
    private String parkingLotName;

    /** 车牌号 */
    private String plateNumber;

    /** 入场时间 */
    private LocalDateTime entryTime;

    /** 出场时间 */
    private LocalDateTime exitTime;

    /** 在场时长（分钟） */
    private Long durationMinutes;

    /** 费用（元） */
    private BigDecimal feeAmount;

    /** 支付状态：UNPAID-未支付, PAID-已支付, FREE-免费 */
    private String payStatus;

    /** 状态：IN-在场, OUT-已出场 */
    private String status;
}
