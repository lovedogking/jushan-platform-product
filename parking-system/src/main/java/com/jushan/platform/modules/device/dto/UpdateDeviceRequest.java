package com.jushan.platform.modules.device.dto;

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

    /** 识别后自动语音播报开关（0=关闭, 1=开启） */
    private Integer voiceEnabled;

    /** 准入语音模板，{plate}=车牌占位符 */
    private String voiceWelcomeTemplate;

    /** 禁入语音模板，{plate}=车牌占位符 */
    private String voiceDenyTemplate;

    private String voiceReleaseTemplate;

    private Integer displayTextColor;
    private Integer displayRotateMode;
    private Integer displayBrightness;
    private Integer displayVolume;

    private Integer voiceVolume;
    private Integer voiceMale;

    private String voiceEntryWelcomeTemplate;
    private String voiceExitWelcomeTemplate;
    private String displayEntryWelcomeTemplate;
    private String displayExitWelcomeTemplate;

    /** 准入显示屏模板，{plate}=车牌占位符 */
    private String displayWelcomeTemplate;

    /** 禁入显示屏模板，{plate}=车牌占位符 */
    private String displayDenyTemplate;

    /** 待机默认显示文字 */
    private String displayIdleText;

    /** 识别联动显示停留秒数 */
    private Integer displayDurationSec;

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

    public Integer getVoiceEnabled() { return voiceEnabled; }
    public void setVoiceEnabled(Integer voiceEnabled) { this.voiceEnabled = voiceEnabled; }

    public String getVoiceWelcomeTemplate() { return voiceWelcomeTemplate; }
    public void setVoiceWelcomeTemplate(String voiceWelcomeTemplate) { this.voiceWelcomeTemplate = voiceWelcomeTemplate; }

    public String getVoiceDenyTemplate() { return voiceDenyTemplate; }
    public void setVoiceDenyTemplate(String voiceDenyTemplate) { this.voiceDenyTemplate = voiceDenyTemplate; }

    public String getVoiceReleaseTemplate() { return voiceReleaseTemplate; }
    public void setVoiceReleaseTemplate(String voiceReleaseTemplate) { this.voiceReleaseTemplate = voiceReleaseTemplate; }

    public String getDisplayWelcomeTemplate() { return displayWelcomeTemplate; }
    public void setDisplayWelcomeTemplate(String displayWelcomeTemplate) { this.displayWelcomeTemplate = displayWelcomeTemplate; }

    public String getDisplayDenyTemplate() { return displayDenyTemplate; }
    public void setDisplayDenyTemplate(String displayDenyTemplate) { this.displayDenyTemplate = displayDenyTemplate; }

    public String getDisplayIdleText() { return displayIdleText; }
    public void setDisplayIdleText(String displayIdleText) { this.displayIdleText = displayIdleText; }

    public Integer getDisplayDurationSec() { return displayDurationSec; }
    public void setDisplayDurationSec(Integer displayDurationSec) { this.displayDurationSec = displayDurationSec; }

    public Integer getDisplayTextColor() { return displayTextColor; }
    public void setDisplayTextColor(Integer displayTextColor) { this.displayTextColor = displayTextColor; }

    public Integer getDisplayRotateMode() { return displayRotateMode; }
    public void setDisplayRotateMode(Integer displayRotateMode) { this.displayRotateMode = displayRotateMode; }

    public Integer getDisplayBrightness() { return displayBrightness; }
    public void setDisplayBrightness(Integer displayBrightness) { this.displayBrightness = displayBrightness; }

    public Integer getDisplayVolume() { return displayVolume; }
    public void setDisplayVolume(Integer displayVolume) { this.displayVolume = displayVolume; }

    public Integer getVoiceVolume() { return voiceVolume; }
    public void setVoiceVolume(Integer voiceVolume) { this.voiceVolume = voiceVolume; }

    public Integer getVoiceMale() { return voiceMale; }
    public void setVoiceMale(Integer voiceMale) { this.voiceMale = voiceMale; }

    public String getVoiceEntryWelcomeTemplate() { return voiceEntryWelcomeTemplate; }
    public void setVoiceEntryWelcomeTemplate(String v) { this.voiceEntryWelcomeTemplate = v; }
    public String getVoiceExitWelcomeTemplate() { return voiceExitWelcomeTemplate; }
    public void setVoiceExitWelcomeTemplate(String v) { this.voiceExitWelcomeTemplate = v; }
    public String getDisplayEntryWelcomeTemplate() { return displayEntryWelcomeTemplate; }
    public void setDisplayEntryWelcomeTemplate(String v) { this.displayEntryWelcomeTemplate = v; }
    public String getDisplayExitWelcomeTemplate() { return displayExitWelcomeTemplate; }
    public void setDisplayExitWelcomeTemplate(String v) { this.displayExitWelcomeTemplate = v; }
}
