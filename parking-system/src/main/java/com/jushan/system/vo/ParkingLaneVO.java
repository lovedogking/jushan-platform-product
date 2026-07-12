package com.jushan.system.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车道视图对象（T19 + T21 设备绑定扩展）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingLaneVO {

    private Long id;
    private Long parkingLotId;
    private String name;
    private String code;
    private String direction;
    private String status;
    private Integer isKeyLane;
    private String autoReleasePolicy;
    private String description;
    /** 绑定到本条车道的设备列表（T21） */
    private List<DeviceVO> devices;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getIsKeyLane() { return isKeyLane; }
    public void setIsKeyLane(Integer isKeyLane) { this.isKeyLane = isKeyLane; }

    public String getAutoReleasePolicy() { return autoReleasePolicy; }
    public void setAutoReleasePolicy(String autoReleasePolicy) { this.autoReleasePolicy = autoReleasePolicy; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<DeviceVO> getDevices() { return devices; }
    public void setDevices(List<DeviceVO> devices) { this.devices = devices; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
