package com.jushan.system.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 月卡视图 VO（Phase 1 A1）。
 * <p>
 * 仅展示月卡相关字段，不与普通车辆管理视图混淆。
 * 基于 {@code sys_vehicle} 表 + vehicleType=MONTHLY 过滤。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MonthlyPassVO {

    /** 车辆 ID */
    private Long id;
    /** 车牌号 */
    private String plateNumber;
    /** 车牌颜色 */
    private String plateColor;
    /** 车场 ID */
    private Long parkingLotId;
    /** 车场名称 */
    private String parkingLotName;
    /** 车辆类型 */
    private String vehicleType;
    /** 有效期起 */
    private LocalDate validStartDate;
    /** 有效期止 */
    private LocalDate validEndDate;
    /** 状态（ACTIVE/EXPIRED/DISABLED） */
    private String status;
    /** 车主姓名 */
    private String ownerName;
    /** 车主电话 */
    private String ownerPhone;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public String getPlateColor() { return plateColor; }
    public void setPlateColor(String plateColor) { this.plateColor = plateColor; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public LocalDate getValidStartDate() { return validStartDate; }
    public void setValidStartDate(LocalDate validStartDate) { this.validStartDate = validStartDate; }
    public LocalDate getValidEndDate() { return validEndDate; }
    public void setValidEndDate(LocalDate validEndDate) { this.validEndDate = validEndDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
