package com.jushan.system.vo;

import java.time.LocalDateTime;

/**
 * 平台设备台账视图对象（T20）。
 * <p>
 * 包含厂商名称和型号名称等关联信息，但不包含 vendor_id 和 model_id 的内部主键。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class DeviceVO {

    private Long id;
    private Long parkingLotId;
    private Long laneId;
    private Long executorDeviceId;
    private Long vendorId;
    private String vendorName;
    private Long modelId;
    private String modelName;
    private String name;
    private String code;
    private String deviceSn;
    private String deviceType;
    private String status;
    private String capabilities;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getExecutorDeviceId() { return executorDeviceId; }
    public void setExecutorDeviceId(Long executorDeviceId) { this.executorDeviceId = executorDeviceId; }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDeviceSn() { return deviceSn; }
    public void setDeviceSn(String deviceSn) { this.deviceSn = deviceSn; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
