package com.jushan.platform.modules.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台设备台账实体（T20）。
 * <p>
 * 本实体是平台设备业务配置的<b>唯一主数据源</b>。
 * 设备通过 parking_lot_id → tenant_id 推导租户范围。
 * device_sn 在同厂商内唯一（UNIQUE(vendor_id, device_sn)），是调用 Device Access 的可信映射。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>前端只能提交平台设备 ID，禁止直接传入 device_sn</li>
 *   <li>后端从本表可信记录读取 device_sn</li>
 *   <li>SN 不能跨租户/停车场复用</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("device")
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属停车场 ID */
    private Long parkingLotId;

    /** 绑定车道 ID（T21 负责绑定，当前可为空） */
    private Long laneId;

    /**
     * 执行设备 ID（仅 GATE 设备使用）。
     * <p>
     * 指向实际控制继电器开闸的 CAMERA 设备 ID。
     * CAMERA 设备此字段为 null（自身即为执行者）。
     * </p>
     */
    private Long executorDeviceId;

    /** 设备厂商 ID */
    private Long vendorId;

    /** 设备型号 ID */
    private Long modelId;

    /** 设备名称（运营可读） */
    private String name;

    /** 设备业务编码（停车场内唯一） */
    private String code;

    /** 厂商设备序列号（可信记录，禁止前端传入） */
    private String deviceSn;

    /** 设备类型：CAMERA-相机, GATE-道闸 */
    private String deviceType;

    /**
     * 识别方向（仅 CAMERA 使用）。
     * <p>1=入场(ENTRY), 2=出场(EXIT), NULL=未指定。</p>
     * <p>双向通道绑定的相机必须指定识别方向。</p>
     */
    private Integer recognitionDirection;

    /**
     * 主备角色（仅 CAMERA 使用）。
     * <p>1=主相机, 2=备相机, NULL=单相机模式或无主备。</p>
     */
    private Integer cameraRole;

    /** 状态：ENABLED-启用, DISABLED-停用 */
    private String status;

    /** 设备能力（JSON 或逗号分隔，如 RECOGNIZE,CAPTURE,GATE_OPEN） */
    private String capabilities;

    /** 备注 */
    private String description;

    /** 设备 IP 地址 */
    private String ipAddress;

    /** 设备端口，默认 80 */
    private Integer port;

    /** 子网掩码 */
    private String subnetMask;

    /** 网关地址 */
    private String gateway;

    /** 识别后自动语音播报开关（0=关闭, 1=开启） */
    private Integer voiceEnabled;

    /** 准入语音模板，{plate}=车牌占位符，如 "{plate},欢迎光临" */
    private String voiceWelcomeTemplate;

    /** 禁入语音模板，{plate}=车牌占位符，如 "{plate},禁止通行" */
    private String voiceDenyTemplate;

    /** 手动放行语音模板（支持 {plate}, {type} 占位符） */
    @TableField("voice_release_template")
    private String voiceReleaseTemplate;

    /** 入场欢迎语音模板 */
    private String voiceEntryWelcomeTemplate;

    /** 出场欢送语音模板 */
    private String voiceExitWelcomeTemplate;

    /** 入场欢迎显示模板 */
    private String displayEntryWelcomeTemplate;

    /** 出场欢送显示模板 */
    private String displayExitWelcomeTemplate;

    /** 显示屏文字颜色 0白/1红/2蓝/3绿 */
    private Integer displayTextColor;

    /** 显示屏翻转方向 0正常/1上下翻转 */
    private Integer displayRotateMode;

    /** 显示屏亮度 0-5 */
    private Integer displayBrightness;

    /** 显示屏音量 0-5 */
    private Integer displayVolume;

    /** 语音音量 1-100 */
    private Integer voiceVolume;

    /** 语音类型 0男声/1女声 */
    private Integer voiceMale;

    /** 准入显示屏模板，{plate}=车牌占位符，\n=换行 */
    private String displayWelcomeTemplate;

    /** 禁入显示屏模板，{plate}=车牌占位符，\n=换行 */
    private String displayDenyTemplate;

    /** 待机默认显示文字，识别联动结束后恢复 */
    private String displayIdleText;

    /** 识别联动显示停留秒数，默认5，0=不自动恢复 */
    private Integer displayDurationSec;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getExecutorDeviceId() { return executorDeviceId; }
    public void setExecutorDeviceId(Long executorDeviceId) { this.executorDeviceId = executorDeviceId; }

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

    public String getVoiceReleaseTemplate() { return voiceReleaseTemplate; }
    public void setVoiceReleaseTemplate(String voiceReleaseTemplate) { this.voiceReleaseTemplate = voiceReleaseTemplate; }

    public String getVoiceEntryWelcomeTemplate() { return voiceEntryWelcomeTemplate; }
    public void setVoiceEntryWelcomeTemplate(String v) { this.voiceEntryWelcomeTemplate = v; }

    public String getVoiceExitWelcomeTemplate() { return voiceExitWelcomeTemplate; }
    public void setVoiceExitWelcomeTemplate(String v) { this.voiceExitWelcomeTemplate = v; }

    public String getDisplayEntryWelcomeTemplate() { return displayEntryWelcomeTemplate; }
    public void setDisplayEntryWelcomeTemplate(String v) { this.displayEntryWelcomeTemplate = v; }

    public String getDisplayExitWelcomeTemplate() { return displayExitWelcomeTemplate; }
    public void setDisplayExitWelcomeTemplate(String v) { this.displayExitWelcomeTemplate = v; }

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
