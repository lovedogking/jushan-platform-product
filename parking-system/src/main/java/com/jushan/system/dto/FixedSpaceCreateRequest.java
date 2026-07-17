package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 固定车位绑定请求 DTO（Phase 1 A2）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class FixedSpaceCreateRequest {

    @NotNull(message = "车场不能为空")
    private Long parkingLotId;

    /** 区域ID（可选） */
    private Long zoneId;

    @NotBlank(message = "车位号不能为空")
    private String spaceNo;

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    @NotNull(message = "有效期起不能为空")
    private LocalDate validStart;

    @NotNull(message = "有效期止不能为空")
    private LocalDate validEnd;

    /** 实收金额（分） */
    @NotNull(message = "实收金额不能为空")
    private Integer paidAmountCents;

    /** 缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER */
    @NotBlank(message = "缴费方式不能为空")
    private String payMethod;

    /** 来源：ADMIN-运营端 / MINIAPP-小程序端（默认 ADMIN） */
    private String source;

    private String remark;

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getSpaceNo() { return spaceNo; }
    public void setSpaceNo(String spaceNo) { this.spaceNo = spaceNo; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public LocalDate getValidStart() { return validStart; }
    public void setValidStart(LocalDate validStart) { this.validStart = validStart; }
    public LocalDate getValidEnd() { return validEnd; }
    public void setValidEnd(LocalDate validEnd) { this.validEnd = validEnd; }
    public Integer getPaidAmountCents() { return paidAmountCents; }
    public void setPaidAmountCents(Integer paidAmountCents) { this.paidAmountCents = paidAmountCents; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
