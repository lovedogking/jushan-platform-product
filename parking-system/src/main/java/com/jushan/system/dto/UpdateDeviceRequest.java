package com.jushan.system.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新设备请求（T20）。
 * <p>
 * 仅非 null 字段被更新。device_sn 不可通过此接口修改（需走专门的 SN 变更审计流程）。
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

    // ==================== getter / setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

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
