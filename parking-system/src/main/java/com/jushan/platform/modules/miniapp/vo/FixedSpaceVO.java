package com.jushan.platform.modules.miniapp.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 固定车位绑定关系视图 VO（Phase 1 A2）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class FixedSpaceVO {

    /** 绑定记录 ID */
    private Long id;
    /** 车场 ID */
    private Long parkingLotId;
    /** 车场名称 */
    private String parkingLotName;
    /** 区域 ID */
    private Long zoneId;
    /** 区域名称 */
    private String zoneName;
    /** 车位号 */
    private String spaceNo;
    /** 车辆 ID */
    private Long vehicleId;
    /** 车牌号 */
    private String plateNumber;
    /** 有效期起 */
    private LocalDate validStart;
    /** 有效期止 */
    private LocalDate validEnd;
    /** 状态（1=生效中, 2=已过期, 3=已注销） */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER */
    private String payMethod;
    /** 实收金额（分） */
    private Integer paidAmountCents;
    /** 审核状态：PENDING-待审核 / APPROVED-已通过 / REJECTED-已驳回 */
    private String reviewStatus;
    /** 审核备注 */
    private String reviewRemark;
    /** 来源：ADMIN-运营端 / MINIAPP-小程序端 */
    private String source;
    /** 申请人ID（小程序用户ID；运营端录入为NULL） */
    private Long applicantId;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public String getSpaceNo() { return spaceNo; }
    public void setSpaceNo(String spaceNo) { this.spaceNo = spaceNo; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public LocalDate getValidStart() { return validStart; }
    public void setValidStart(LocalDate validStart) { this.validStart = validStart; }
    public LocalDate getValidEnd() { return validEnd; }
    public void setValidEnd(LocalDate validEnd) { this.validEnd = validEnd; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
