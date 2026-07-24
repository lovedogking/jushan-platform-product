package com.jushan.platform.modules.device.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Device Access v0.2 设备状态查询响应 data。
 * <p>
 * 对应 {@code GET /api/v1/devices/{deviceId}/status} 的 {@code data} 字段。
 * 当前设备状态以 Device Access 返回为准，平台不做二次计算。
 * <p>
 * <strong>字段说明</strong>：基于 DA v0.2 代码推断，具体字段待真机联调确认。
 * 所有未知字段兼容忽略。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceStatusDTO {

    /** 设备 SN */
    private String deviceSn;

    /** 是否在线 */
    private Boolean online;

    /** 最近在线时间（ISO 格式） */
    private String lastOnlineTime;

    /** 设备当前状态描述 */
    private String status;

    /** 道闸杆状态（如 open/closed/unknown），仅 GATE 类型设备有效 */
    private String gateStatus;

    /** 道闸连接状态（如 connected/disconnected/unknown），仅 GATE 类型设备有效 */
    private String gateConnectStatus;

    // ==================== getter / setter ====================

    public String getDeviceSn() { return deviceSn; }
    public void setDeviceSn(String deviceSn) { this.deviceSn = deviceSn; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }

    public String getLastOnlineTime() { return lastOnlineTime; }
    public void setLastOnlineTime(String lastOnlineTime) { this.lastOnlineTime = lastOnlineTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGateStatus() { return gateStatus; }
    public void setGateStatus(String gateStatus) { this.gateStatus = gateStatus; }

    public String getGateConnectStatus() { return gateConnectStatus; }
    public void setGateConnectStatus(String gateConnectStatus) { this.gateConnectStatus = gateConnectStatus; }

    @Override
    public String toString() {
        return "DeviceStatusDTO{" +
                "deviceSn='" + deviceSn + '\'' +
                ", online=" + online +
                ", lastOnlineTime='" + lastOnlineTime + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
