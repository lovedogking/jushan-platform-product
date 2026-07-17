package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 停车订单实体（Sprint 8 完整版）。
 * <p>
 * 记录车辆出场时生成的待支付/已支付订单。支持状态机、支付单关联、回调幂等。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>{@code tenant_id} 和 {@code parking_lot_id} 从可信停车记录推导</li>
 *   <li>金额使用整数分，禁止负值</li>
 *   <li>状态更新使用条件更新（version 乐观锁），禁止整实体覆盖</li>
 *   <li>订单号全局唯一，幂等键 24h 内重复返回首次结果</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("parking_order")
public class ParkingOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（从停车记录推导） */
    private Long tenantId;

    /** 停车场 ID（从停车记录推导） */
    private Long parkingLotId;

    /** 关联停车记录 ID */
    private Long parkingRecordId;

    /** 关联业务主键（MONTH_RENEW 订单时为 vehicle_id） */
    private Long refId;

    /** 续费月数（月卡续费订单） */
    private Integer renewalMonths;

    /** 发起续费的操作人 ID（sys_user.id） */
    private Long operatorId;

    /** 订单号：O{lotId}{yyyyMMdd}{6位序号} */
    private String orderNo;

    /** 订单类型：PARKING-停车, MONTH_RENEW-月卡续费, VISITOR-访客, TOP_UP-充值 */
    private String orderType;

    /** 车牌号（标准化后） */
    private String plateNumber;

    /** 订单金额（分），原始应收，禁止负值 */
    private Integer amountCents;

    /** 优惠金额（分） */
    private Integer discountAmount;

    /** 积分抵扣金额（分） */
    private Integer pointsDiscount;

    /** 应付金额（分） */
    private Integer payableAmount;

    /** 已支付金额（分） */
    private Integer paidAmount;

    /** 状态：PENDING_PAY-待支付, PAYING-支付中, PAID-已支付, COMPLETED-已完成, CANCELLED-已取消, PAY_FAILED-支付失败, REFUNDING-退款中, REFUNDED-已退款 */
    private String status;

    /** 支付渠道：PYUN-P云, WECHAT-微信, ALIPAY-支付宝, CASH-现金, BALANCE-余额 */
    private String payChannel;

    /** P云支付流水号 */
    private String paySerial;

    /** 幂等键 X-Idempotency-Key */
    private String idempotencyKey;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 出场时间（订单完成时） */
    private LocalDateTime exitTime;

    /** 订单过期时间 */
    private LocalDateTime expiredAt;

    /** 退款原因（任务包 1-2 模拟退款必填） */
    private String refundReason;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 退款操作人（sys_user.id） */
    private Long refundOperatorId;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 逻辑删除时间（NULL 表示未删除） */
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== 常量定义 ====================

    public static final String ORDER_TYPE_PARKING = "PARKING";
    public static final String ORDER_TYPE_MONTH_RENEW = "MONTH_RENEW";
    public static final String ORDER_TYPE_VISITOR = "VISITOR";
    public static final String ORDER_TYPE_TOP_UP = "TOP_UP";

    /** 预订单（入场生成，尚未计费）——任务包 1-2 */
    public static final String STATUS_PRE_ORDER = "PRE_ORDER";
    public static final String STATUS_PENDING_PAY = "PENDING_PAY";
    public static final String STATUS_PAYING = "PAYING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_PAY_FAILED = "PAY_FAILED";
    /** 欠费中——任务包 1-2 */
    public static final String STATUS_ARREARS = "ARREARS";
    public static final String STATUS_REFUNDING = "REFUNDING";
    public static final String STATUS_REFUNDED = "REFUNDED";

    public static final String PAY_CHANNEL_PYUN = "PYUN";
    public static final String PAY_CHANNEL_WECHAT = "WECHAT";
    public static final String PAY_CHANNEL_ALIPAY = "ALIPAY";
    public static final String PAY_CHANNEL_CASH = "CASH";
    public static final String PAY_CHANNEL_BALANCE = "BALANCE";

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getParkingRecordId() { return parkingRecordId; }
    public void setParkingRecordId(Long parkingRecordId) { this.parkingRecordId = parkingRecordId; }

    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }

    public Integer getRenewalMonths() { return renewalMonths; }
    public void setRenewalMonths(Integer renewalMonths) { this.renewalMonths = renewalMonths; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

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

    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }

    public String getPaySerial() { return paySerial; }
    public void setPaySerial(String paySerial) { this.paySerial = paySerial; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    public LocalDateTime getRefundTime() { return refundTime; }
    public void setRefundTime(LocalDateTime refundTime) { this.refundTime = refundTime; }

    public Long getRefundOperatorId() { return refundOperatorId; }
    public void setRefundOperatorId(Long refundOperatorId) { this.refundOperatorId = refundOperatorId; }
}
