package com.jushan.platform.modules.miniapp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 固定车位绑定关系（Phase 1 A2）。
 * <p>
 * 记录「车位号 ↔ 车辆」的专属绑定关系。
 * 非绑定车辆占用该车位时按临停计费。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("fixed_space_binding")
public class FixedSpaceBinding implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：生效中 */
    public static final int STATUS_ACTIVE = 1;
    /** 状态：已过期 */
    public static final int STATUS_EXPIRED = 2;
    /** 状态：已注销 */
    public static final int STATUS_DISABLED = 3;

    /** 审核状态：待审核 */
    public static final String REVIEW_PENDING = "PENDING";
    /** 审核状态：已通过 */
    public static final String REVIEW_APPROVED = "APPROVED";
    /** 审核状态：已驳回 */
    public static final String REVIEW_REJECTED = "REJECTED";

    /** 缴费方式：现金 */
    public static final String PAY_METHOD_CASH = "CASH";
    /** 缴费方式：线下转账 */
    public static final String PAY_METHOD_OFFLINE_TRANSFER = "OFFLINE_TRANSFER";
    /** 缴费方式：模拟支付 */
    public static final String PAY_METHOD_SIMULATED_PAY = "SIMULATED_PAY";
    /** 缴费方式：其他 */
    public static final String PAY_METHOD_OTHER = "OTHER";

    /** 来源：运营端 */
    public static final String SOURCE_ADMIN = "ADMIN";
    /** 来源：小程序端 */
    public static final String SOURCE_MINIAPP = "MINIAPP";

    private Long id;
    private Long tenantId;
    private Long parkingLotId;
    private Long zoneId;
    private String spaceNo;
    private Long vehicleId;
    private LocalDate validStart;
    private LocalDate validEnd;
    private Integer status;
    private String payMethod;
    private Integer paidAmountCents;
    private String reviewStatus;
    /** 审核备注 */
    private String reviewRemark;
    private String source;
    private Long applicantId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getSpaceNo() { return spaceNo; }
    public void setSpaceNo(String spaceNo) { this.spaceNo = spaceNo; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public LocalDate getValidStart() { return validStart; }
    public void setValidStart(LocalDate validStart) { this.validStart = validStart; }
    public LocalDate getValidEnd() { return validEnd; }
    public void setValidEnd(LocalDate validEnd) { this.validEnd = validEnd; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public Integer getPaidAmountCents() { return paidAmountCents; }
    public void setPaidAmountCents(Integer paidAmountCents) { this.paidAmountCents = paidAmountCents; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
