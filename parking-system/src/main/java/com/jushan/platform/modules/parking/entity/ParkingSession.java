package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 在场车辆记录实体。
 * <p>
 * 记录车辆从入场到出场的完整状态，支持重复入场策略。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_session")
public class ParkingSession extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 停车场ID */
    private Long parkingLotId;

    /** 入场通道ID */
    private Long laneId;

    /** 车牌号（大写） */
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型判定结果 */
    private String vehicleType;

    /** 入场时间 */
    private LocalDateTime entryTime;

    /** 入场抓拍图片URL */
    private String entryImage;

    /** 入场操作人ID（人工放行时） */
    private Long entryOperator;

    /** 出场时间 */
    private LocalDateTime exitTime;

    /** 出场通道ID */
    private Long exitLaneId;

    /** 出场抓拍图片URL */
    private String exitImage;

    /** 出场操作人ID */
    private Long exitOperator;

    /** 状态：IN-在场, OUT-已出场, EXCEPTION-异常 */
    private String status;

    /** 应收费用（元） */
    private BigDecimal feeAmount;

    /** 已付费用（元） */
    private BigDecimal paidAmount;

    /** 关联订单ID */
    private Long orderId;

    /** 关联停车记录ID（ParkingRecord.id） */
    private Long parkingRecordId;

    /** 备注 */
    private String remark;

    // ==================== 常量定义 ====================

    public static final String STATUS_IN = "IN";
    public static final String STATUS_OUT = "OUT";
    public static final String STATUS_EXCEPTION = "EXCEPTION";
}
