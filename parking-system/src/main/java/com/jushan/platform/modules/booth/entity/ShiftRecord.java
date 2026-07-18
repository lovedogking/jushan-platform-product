package com.jushan.platform.modules.booth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交接班记录实体。
 * <p>
 * 记录岗亭操作员开班/交班信息及本班汇总数据。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shift_record")
public class ShiftRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 停车场ID */
    private Long parkingLotId;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    private String operatorName;

    /** 班次类型：MORNING-早班, AFTERNOON-中班, NIGHT-晚班 */
    private String shiftType;

    /** 开班时间 */
    private LocalDateTime startTime;

    /** 交班时间 */
    private LocalDateTime endTime;

    /** 本班入场车辆数 */
    private Integer entryCount;

    /** 本班出场车辆数 */
    private Integer exitCount;

    /** 本班收费金额（元） */
    private BigDecimal feeAmount;

    /** 现金收费金额（元） */
    private BigDecimal cashAmount;

    /** 线上收费金额（元） */
    private BigDecimal onlineAmount;

    /** 异常处理数 */
    private Integer exceptionCount;

    /** 交接状态：OPEN-开班中, CLOSED-已交班 */
    private String handoverStatus;

    /** 接班人ID */
    private Long handoverTo;

    /** 交接备注 */
    private String handoverRemark;

    /** 手工校正实收金额原因 */
    private String adjustReason;

    /** 本班产生的欠费订单数 */
    private Integer arrearsCount;

    /** 交接给下一班的未支付/欠费订单数量 */
    private Integer handoverOrderCount;

    // ==================== 常量定义 ====================

    public static final String SHIFT_MORNING = "MORNING";
    public static final String SHIFT_AFTERNOON = "AFTERNOON";
    public static final String SHIFT_NIGHT = "NIGHT";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";
}
