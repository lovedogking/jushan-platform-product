package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建设备请求（T20）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CreateDeviceRequest {

    /** 所属停车场 ID */
    @NotNull(message = "所属停车场不能为空")
    private Long parkingLotId;

    /** 设备厂商 ID */
    @NotNull(message = "设备厂商不能为空")
    private Long vendorId;

    /** 设备型号 ID */
    @NotNull(message = "设备型号不能为空")
    private Long modelId;

    /** 设备名称（运营可读） */
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称最长128个字符")
    private String name;

    /** 设备业务编码（停车场内唯一） */
    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码最长64个字符")
    private String code;

    /** 厂商设备序列号（可信记录） */
    @NotBlank(message = "设备序列号不能为空")
    @Size(max = 128, message = "设备序列号最长128个字符")
    private String deviceSn;

    /** 设备类型：CAMERA-相机, GATE-道闸 */
    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    /** 识别方向：1=入场, 2=出场（仅 CAMERA 使用，可选） */
    private Integer recognitionDirection;

    /** 主备角色：1=主相机, 2=备相机（仅 CAMERA 使用，可选） */
    private Integer cameraRole;

    /** 设备能力（逗号分隔，如 RECOGNIZE,CAPTURE,GATE_OPEN） */
    @Size(max = 500, message = "设备能力描述最长500个字符")
    private String capabilities;

    /** 备注 */
    @Size(max = 255, message = "备注最长255个字符")
    private String description;

    // ==================== getter / setter ====================

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDeviceSn() { return deviceSn; }
    public void setDeviceSn(String deviceSn) { this.deviceSn = deviceSn; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public Integer getRecognitionDirection() { return recognitionDirection; }
    public void setRecognitionDirection(Integer recognitionDirection) { this.recognitionDirection = recognitionDirection; }

    public Integer getCameraRole() { return cameraRole; }
    public void setCameraRole(Integer cameraRole) { this.cameraRole = cameraRole; }

    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
