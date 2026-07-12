package com.jushan.system.vo;

import java.time.LocalDateTime;

/**
 * 设备状态视图对象（T24）。
 * <p>
 * 包含设备基础信息 + 最新状态快照，供前端列表和详情页展示。
 * {@link #snapshotAgeSeconds} 用于标识状态新鲜度，过期快照需在前端显著标注。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class DeviceStatusVO {

    // ========== 设备基础信息 ==========

    private Long deviceId;
    private String deviceName;
    private String deviceCode;
    private String deviceType;
    private String deviceStatus;

    // ========== 状态快照（可能为 null，表示从未查询过） ==========

    /** 是否在线（来自 DA，可能为 null 表示未知） */
    private Boolean online;

    /** 最近在线时间 */
    private LocalDateTime lastOnlineTime;

    /** 道闸杆状态 */
    private String gateStatus;

    /** 道闸连接状态 */
    private String gateConnectStatus;

    /** 设备状态描述 */
    private String statusDescription;

    // ========== 快照新鲜度 ==========

    /** 状态采集时间 */
    private LocalDateTime collectedAt;

    /** 快照已过秒数（当前时间 - collectedAt），用于前端显示"X 秒前" */
    private Long snapshotAgeSeconds;

    /** 快照是否过期（超过阈值后标记为 stale） */
    private Boolean stale;

    // ========== 查询结果 ==========

    /** 最后一次查询是否成功 */
    private Boolean lastQuerySuccess;

    /** 最后一次查询失败的错误码 */
    private String lastErrorCode;

    /** 最后一次查询失败的错误消息 */
    private String lastErrorMessage;

    // ==================== getter / setter ====================

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getDeviceStatus() { return deviceStatus; }
    public void setDeviceStatus(String deviceStatus) { this.deviceStatus = deviceStatus; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }

    public LocalDateTime getLastOnlineTime() { return lastOnlineTime; }
    public void setLastOnlineTime(LocalDateTime lastOnlineTime) { this.lastOnlineTime = lastOnlineTime; }

    public String getGateStatus() { return gateStatus; }
    public void setGateStatus(String gateStatus) { this.gateStatus = gateStatus; }

    public String getGateConnectStatus() { return gateConnectStatus; }
    public void setGateConnectStatus(String gateConnectStatus) { this.gateConnectStatus = gateConnectStatus; }

    public String getStatusDescription() { return statusDescription; }
    public void setStatusDescription(String statusDescription) { this.statusDescription = statusDescription; }

    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }

    public Long getSnapshotAgeSeconds() { return snapshotAgeSeconds; }
    public void setSnapshotAgeSeconds(Long snapshotAgeSeconds) { this.snapshotAgeSeconds = snapshotAgeSeconds; }

    public Boolean getStale() { return stale; }
    public void setStale(Boolean stale) { this.stale = stale; }

    public Boolean getLastQuerySuccess() { return lastQuerySuccess; }
    public void setLastQuerySuccess(Boolean lastQuerySuccess) { this.lastQuerySuccess = lastQuerySuccess; }

    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
}
