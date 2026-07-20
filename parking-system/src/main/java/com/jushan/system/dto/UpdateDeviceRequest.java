package com.jushan.system.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新设备请求（T20）。
 * <p>
 * 仅非 null 字段被更新。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class UpdateDeviceRequest {

    /** 设备名称 */
    @Size(max = 128, message = "设备名称最长128个字符")
    private String name;

    /** 设备业务编码（停车场内唯一） */
    @Size(max = 64, message = "设备编码最长64个字符")
    private String code;

    /** 设备厂商 ID */
    private Long vendorId;

    /** 设备型号 ID */
    private Long modelId;

    /** 厂商设备序列号（同厂商内唯一） */
    @Size(max = 128, message = "设备序列号最长128个字符")
    private String deviceSn;

    /** 设备类型 */
    private String deviceType;

    /** 识别方向：1=入场, 2=出场（仅 CAMERA 使用） */
    private Integer recognitionDirection;

    /** 主备角色：1=主相机, 2=备相机（仅 CAMERA 使用） */
    private Integer cameraRole;

    /** 设备能力 */
    @Size(max = 500, message = "设备能力描述最长500个字符")
    private String capabilities;

    /** 备注 */
    @Size(max = 255, message = "备注最长255个字符")
    private String description;

    /** 设备 IP 地址 */
    private String ipAddress;

    /** 设备端口 */
    private Integer port;

    /** 子网掩码 */
    private String subnetMask;

    /** 网关地址 */
    private String gateway;

    // ==================== getter / setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

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

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getSubnetMask() { return subnetMask; }
    public void setSubnetMask(String subnetMask) { this.subnetMask = subnetMask; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
}
