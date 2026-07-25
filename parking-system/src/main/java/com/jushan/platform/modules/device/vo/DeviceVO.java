package com.jushan.platform.modules.device.vo;

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
    private Integer recognitionDirection;
    private Integer cameraRole;
    private String status;
    private String capabilities;
    private String description;
    private String ipAddress;
    private Integer port;
    private String subnetMask;
    private String gateway;

    /** 识别后自动语音播报开关（0=关闭, 1=开启） */
    private Integer voiceEnabled;

    /** 准入语音模板，{plate}=车牌占位符 */
    private String voiceWelcomeTemplate;

    /** 禁入语音模板，{plate}=车牌占位符 */
    private String voiceDenyTemplate;

    /** 准入显示屏模板 */
    private String displayWelcomeTemplate;

    /** 禁入显示屏模板 */
    private String displayDenyTemplate;

    private String displayIdleText;
    private Integer displayDurationSec;

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

    public Integer getRecognitionDirection() { return recognitionDirection; }
    public void setRecognitionDirection(Integer recognitionDirection) { this.recognitionDirection = recognitionDirection; }

    public Integer getCameraRole() { return cameraRole; }
    public void setCameraRole(Integer cameraRole) { this.cameraRole = cameraRole; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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

    public Integer getVoiceEnabled() { return voiceEnabled; }
    public void setVoiceEnabled(Integer voiceEnabled) { this.voiceEnabled = voiceEnabled; }

    public String getVoiceWelcomeTemplate() { return voiceWelcomeTemplate; }
    public void setVoiceWelcomeTemplate(String voiceWelcomeTemplate) { this.voiceWelcomeTemplate = voiceWelcomeTemplate; }

    public String getVoiceDenyTemplate() { return voiceDenyTemplate; }
    public void setVoiceDenyTemplate(String voiceDenyTemplate) { this.voiceDenyTemplate = voiceDenyTemplate; }

    public String getDisplayWelcomeTemplate() { return displayWelcomeTemplate; }
    public void setDisplayWelcomeTemplate(String displayWelcomeTemplate) { this.displayWelcomeTemplate = displayWelcomeTemplate; }

    public String getDisplayDenyTemplate() { return displayDenyTemplate; }
    public void setDisplayDenyTemplate(String displayDenyTemplate) { this.displayDenyTemplate = displayDenyTemplate; }

    public String getDisplayIdleText() { return displayIdleText; }
    public void setDisplayIdleText(String displayIdleText) { this.displayIdleText = displayIdleText; }

    public Integer getDisplayDurationSec() { return displayDurationSec; }
    public void setDisplayDurationSec(Integer displayDurationSec) { this.displayDurationSec = displayDurationSec; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
