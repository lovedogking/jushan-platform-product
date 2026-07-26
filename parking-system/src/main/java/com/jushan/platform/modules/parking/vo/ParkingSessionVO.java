package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 在场车辆记录视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingSessionVO {

    /** 在场记录ID */
    private Long id;

    /** 停车场ID */
    private Long parkingLotId;

    /** 入场通道ID */
    private Long laneId;

    /** 车牌号 */
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型判定结果 */
    private String vehicleType;

    /** 入场时间 */
    private LocalDateTime entryTime;

    /** 入场抓拍图片URL */
    private String entryImage;

    /** 出场时间 */
    private LocalDateTime exitTime;

    /** 出场通道ID */
    private Long exitLaneId;

    /** 出场抓拍图片URL */
    private String exitImage;

    /** 入场通道名称 */
    private String entryLaneName;

    /** 出场通道名称 */
    private String exitLaneName;

    /** 状态：IN-在场, OUT-已出场, EXCEPTION-异常 */
    private String status;
    private BigDecimal feeAmount;

    /** 应收费用（分），精度安全的整数分表示 */
    private Integer feeCents;

    /** 已付费用（元） */
    private BigDecimal paidAmount;

    /** 在场时长（分钟） */
    private Long durationMinutes;

    /** 入场触发方式 */
    private String entryTrigger;

    /** 入场操作人ID */
    private Long entryOperator;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
