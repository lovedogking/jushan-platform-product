package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 车辆出场记录更新命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingSessionExitCmd {

    /** 在场记录ID */
    @NotNull(message = "在场记录ID不能为空")
    private Long sessionId;

    /** 出场通道ID */
    private Long exitLaneId;

    /** 出场抓拍图片URL */
    @Size(max = 255, message = "图片URL最多255个字符")
    private String exitImage;

    /** 出场操作人ID */
    private Long exitOperator;

    /** 应收费用（元） */
    private BigDecimal feeAmount;

    /** 已付费用（元） */
    private BigDecimal paidAmount;

    /** 关联订单ID */
    private Long orderId;

    /** 关联停车记录ID（ParkingRecord.id） */
    private Long parkingRecordId;

    /** 备注 */
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;
}
