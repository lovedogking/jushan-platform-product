package com.jushan.platform.modules.miniapp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代理支付记录实体（Phase 3 E1）。
 * <p>
 * 记录代缴人替车主支付停车费的行为，用于追溯和通知。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("proxy_pay_record")
public class ProxyPayRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属租户 */
    private Long tenantId;

    /** 车场ID */
    private Long parkingLotId;

    /** 停车订单ID */
    private Long orderId;

    /** 停车记录ID */
    private Long recordId;

    /** 车牌号 */
    private String plateNumber;

    /** 代缴人用户ID */
    private Long payerId;

    /** 代缴人昵称 */
    private String payerName;

    /** 车主用户ID（可为NULL表示未注册车主） */
    private Long ownerId;

    /** 车主昵称 */
    private String ownerName;

    /** 代缴金额（分） */
    private Integer amountCents;

    /** 状态：COMPLETED-已完成 */
    private String status;

    /** 支付流水号 */
    private String paySerial;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    // ==================== Constants ====================

    /** 已完成 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Long getPayerId() { return payerId; }
    public void setPayerId(Long payerId) { this.payerId = payerId; }

    public String getPayerName() { return payerName; }
    public void setPayerName(String payerName) { this.payerName = payerName; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaySerial() { return paySerial; }
    public void setPaySerial(String paySerial) { this.paySerial = paySerial; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
