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
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
