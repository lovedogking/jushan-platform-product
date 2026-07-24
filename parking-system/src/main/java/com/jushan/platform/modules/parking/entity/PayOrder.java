package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付流水实体（P云回调幂等）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("pay_order")
public class PayOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long parkingLotId;
    private Long orderId;

    /** 支付请求单号 */
    private String payOrderNo;

    /** P云支付流水号 */
    private String paySerial;

    /** 退款请求单号 */
    private String refundOrderNo;

    /** P云退款流水号 */
    private String refundSerial;

    /** 支付渠道：PYUN, WECHAT, ALIPAY, CASH, BALANCE */
    private String payChannel;

    /** 支付金额（分） */
    private Integer payAmount;

    /** 退款金额（分） */
    private Integer refundAmount;

    /** 状态：PENDING-待支付, SUCCESS-成功, FAILED-失败, REFUNDED-已退款 */
    private String status;

    /** 付款方 OpenID */
    private String payerOpenId;

    /** 支付场景：MINI_APP, H5, POS, BOOTH */
    private String payScene;

    /** 第三方支付渠道交易单号 */
    private String tradeNo;

    /** 交易时间 */
    private LocalDateTime tradeTime;

    /** 手续费（分） */
    private Integer feeCents;

    /** 回调原始报文（JSON） */
    private String notifyRaw;

    /** 幂等键 */
    private String idempotencyKey;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // 常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getPayOrderNo() { return payOrderNo; }
    public void setPayOrderNo(String payOrderNo) { this.payOrderNo = payOrderNo; }
    public String getPaySerial() { return paySerial; }
    public void setPaySerial(String paySerial) { this.paySerial = paySerial; }
    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }
    public String getRefundSerial() { return refundSerial; }
    public void setRefundSerial(String refundSerial) { this.refundSerial = refundSerial; }
    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }
    public Integer getPayAmount() { return payAmount; }
    public void setPayAmount(Integer payAmount) { this.payAmount = payAmount; }
    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer refundAmount) { this.refundAmount = refundAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayerOpenId() { return payerOpenId; }
    public void setPayerOpenId(String payerOpenId) { this.payerOpenId = payerOpenId; }
    public String getPayScene() { return payScene; }
    public void setPayScene(String payScene) { this.payScene = payScene; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public LocalDateTime getTradeTime() { return tradeTime; }
    public void setTradeTime(LocalDateTime tradeTime) { this.tradeTime = tradeTime; }
    public Integer getFeeCents() { return feeCents; }
    public void setFeeCents(Integer feeCents) { this.feeCents = feeCents; }
    public String getNotifyRaw() { return notifyRaw; }
    public void setNotifyRaw(String notifyRaw) { this.notifyRaw = notifyRaw; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
