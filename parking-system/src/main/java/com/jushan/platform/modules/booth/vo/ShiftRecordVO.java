package com.jushan.platform.modules.booth.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交接班记录视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ShiftRecordVO {

    /** 记录ID */
    private Long id;

    /** 停车场ID */
    private Long parkingLotId;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    private String operatorName;

    /** 班次类型 */
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

    /** 交接状态 */
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

    /** 欠费订单列表（仅交班预览时填充） */
    private List<ArrearsOrderItem> arrearsOrders;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 欠费订单项（用于交班预览）。
     */
    @Data
    public static class ArrearsOrderItem {
        private Long orderId;
        private String plateNumber;
        private Integer feeCents;
        private String createdAt;
    }
}
