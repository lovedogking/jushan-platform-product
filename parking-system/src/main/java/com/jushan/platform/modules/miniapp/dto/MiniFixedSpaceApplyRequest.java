package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 小程序固定车位申请请求 DTO（任务包 5-2 固定车位部分）。
 * <p>
 * 对标 {@link MiniMonthlyPassApplyRequest}，额外包含车位号与区域 ID。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class MiniFixedSpaceApplyRequest {

    @NotNull(message = "车场不能为空")
    private Long parkingLotId;

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    @NotBlank(message = "车位号不能为空")
    private String spaceNo;

    /** 区域 ID（必填，用于定位车位所属区域） */
    @NotNull(message = "区域不能为空")
    private Long zoneId;

    /** 车主姓名（选填） */
    private String ownerName;

    /** 车主电话（选填） */
    private String ownerPhone;

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getSpaceNo() { return spaceNo; }
    public void setSpaceNo(String spaceNo) { this.spaceNo = spaceNo; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
}
