package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟支付流水记录。
 * <p>
 * 记录每笔模拟支付的完整生命周期：待支付 → 已支付 / 已超时。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("mock_payment_record")
public class MockPaymentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待支付 */
    public static final int STATUS_PENDING = 1;
    /** 状态：已支付 */
    public static final int STATUS_PAID = 2;
    /** 状态：已超时 */
    public static final int STATUS_TIMEOUT = 3;

    /** 雪花ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 车场ID */
    private Long parkingLotId;

    /** 关联订单ID */
    private Long orderId;

    /** 车牌号 */
    private String plateNumber;

    /** 支付金额（元） */
    private BigDecimal amount;

    /** 状态：1=待支付 2=已支付 3=已超时 */
    private Integer status;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 支付人（用户ID或"系统超时关闭"） */
    private String paidBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public String getPaidBy() { return paidBy; }
    public void setPaidBy(String paidBy) { this.paidBy = paidBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
