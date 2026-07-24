package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 月卡登记请求 DTO（Phase 1 A1）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MonthlyPassCreateRequest {

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    /** 车牌颜色（默认：蓝色），参考 GA36-2018 */
    private String plateColor;

    @NotNull(message = "车场不能为空")
    private Long parkingLotId;

    @NotNull(message = "有效期起不能为空")
    private LocalDate validStartDate;

    @NotNull(message = "有效期止不能为空")
    private LocalDate validEndDate;

    /** 登记费用（分） */
    private Integer amountCents;

    /** 实收金额（分，必填） */
    @NotNull(message = "实收金额不能为空")
    private Integer paidAmountCents;

    /** 缴费方式：CASH / OFFLINE_TRANSFER / OTHER（必填） */
    @NotBlank(message = "缴费方式不能为空")
    private String payMethod;

    /** 车辆类型（小型车/大型车等，选填） */
    private String vehicleType;

    private String ownerName;
    private String ownerPhone;
    private String remark;

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public String getPlateColor() { return plateColor; }
    public void setPlateColor(String plateColor) { this.plateColor = plateColor; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public LocalDate getValidStartDate() { return validStartDate; }
    public void setValidStartDate(LocalDate validStartDate) { this.validStartDate = validStartDate; }
    public LocalDate getValidEndDate() { return validEndDate; }
    public void setValidEndDate(LocalDate validEndDate) { this.validEndDate = validEndDate; }
    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }
    public Integer getPaidAmountCents() { return paidAmountCents; }
    public void setPaidAmountCents(Integer paidAmountCents) { this.paidAmountCents = paidAmountCents; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
