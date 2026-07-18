package com.jushan.system.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单管理视图 VO（B1）。
 * <p>
 * 统一展示临停订单、月卡续费订单、固定车位续费订单。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class OrderAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 订单类型：PARKING-临停, MONTH_RENEW-月卡续费, FIXED_SPACE_RENEW-固定车位续费 */
    private String orderType;

    /** 订单类型中文 */
    private String orderTypeLabel;

    /** 车牌号 */
    private String plateNumber;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 停车场名称 */
    private String parkingLotName;

    /** 订单金额（分） */
    private Integer amountCents;

    /** 优惠金额（分） */
    private Integer discountAmount;

    /** 积分抵扣（分） */
    private Integer pointsDiscount;

    /** 应付金额（分） */
    private Integer payableAmount;

    /** 已支付金额（分） */
    private Integer paidAmount;

    /** 状态 */
    private String status;

    /** 状态中文 */
    private String statusLabel;

    /** 支付渠道 */
    private String payChannel;

    /** 支付渠道中文 */
    private String payChannelLabel;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 入场时间（临停订单） */
    private LocalDateTime entryTime;

    /** 出场时间（临停订单） */
    private LocalDateTime exitTime;

    /** 停车时长（分钟，临停订单） */
    private Integer parkingDurationMinutes;

    /** 操作人姓名 */
    private String operatorName;

    /** 退款原因（任务包 1-2） */
    private String refundReason;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 退款操作人姓名 */
    private String refundOperatorName;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 重算来源订单 ID。超时关单后重算时，新订单记录关联的原订单主键 */
    private Long recalcSourceOrderId;

    /** 重算来源订单号。超时关单后重算时，新订单记录关联的原订单号 */
    private String recalcSourceOrderNo;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getOrderTypeLabel() { return orderTypeLabel; }
    public void setOrderTypeLabel(String orderTypeLabel) { this.orderTypeLabel = orderTypeLabel; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }

    public Integer getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Integer discountAmount) { this.discountAmount = discountAmount; }

    public Integer getPointsDiscount() { return pointsDiscount; }
    public void setPointsDiscount(Integer pointsDiscount) { this.pointsDiscount = pointsDiscount; }

    public Integer getPayableAmount() { return payableAmount; }
    public void setPayableAmount(Integer payableAmount) { this.payableAmount = payableAmount; }

    public Integer getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Integer paidAmount) { this.paidAmount = paidAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }

    public String getPayChannelLabel() { return payChannelLabel; }
    public void setPayChannelLabel(String payChannelLabel) { this.payChannelLabel = payChannelLabel; }

    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public Integer getParkingDurationMinutes() { return parkingDurationMinutes; }
    public void setParkingDurationMinutes(Integer parkingDurationMinutes) { this.parkingDurationMinutes = parkingDurationMinutes; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getRecalcSourceOrderId() { return recalcSourceOrderId; }
    public void setRecalcSourceOrderId(Long recalcSourceOrderId) { this.recalcSourceOrderId = recalcSourceOrderId; }

    public String getRecalcSourceOrderNo() { return recalcSourceOrderNo; }
    public void setRecalcSourceOrderNo(String recalcSourceOrderNo) { this.recalcSourceOrderNo = recalcSourceOrderNo; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    public LocalDateTime getRefundTime() { return refundTime; }
    public void setRefundTime(LocalDateTime refundTime) { this.refundTime = refundTime; }

    public String getRefundOperatorName() { return refundOperatorName; }
    public void setRefundOperatorName(String refundOperatorName) { this.refundOperatorName = refundOperatorName; }
}
