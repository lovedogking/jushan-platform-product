package com.jushan.platform.modules.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备状态快照实体（T24）。
 * <p>
 * 每次对 Device Access 执行 {@code GET /api/v1/devices/{deviceSn}/status} 后持久化结果。
 * 最新快照用于前端展示设备状态，历史快照用于审计和排障。
 * <p>
 * <strong>注意</strong>：本表存储的是<b>查询快照</b>，不是设备实时状态。
 * 前端必须根据 {@link #collectedAt} 显示采集时间，不得将缓存快照冒充实时状态。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("device_status_snapshot")
public class DeviceStatusSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 平台设备 ID（关联 device.id） */
    private Long deviceId;

    /** 查询时使用的厂商序列号（快照冗余，便于排障） */
    private String deviceSn;

    /** 设备是否在线（DA 返回值） */
    private Boolean online;

    /** 最近在线时间（DA 返回值） */
    private LocalDateTime lastOnlineTime;

    /** 道闸杆状态（DA 返回值，仅 GATE 类型有效） */
    private String gateStatus;

    /** 道闸连接状态（DA 返回值，仅 GATE 类型有效） */
    private String gateConnectStatus;

    /** 设备状态描述（DA 返回的 status 字段） */
    private String statusDescription;

    /** 本次查询是否成功（1=成功收到 DA 数据） */
    private Boolean querySuccess;

    /** 失败时的错误码（如 404/503/UNCERTAIN） */
    private String errorCode;

    /** 失败时的错误消息 */
    private String errorMessage;

    /** 状态采集时间（调用 DA 的时间点） */
    private LocalDateTime collectedAt;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceSn() { return deviceSn; }
    public void setDeviceSn(String deviceSn) { this.deviceSn = deviceSn; }

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

    public Boolean getQuerySuccess() { return querySuccess; }
    public void setQuerySuccess(Boolean querySuccess) { this.querySuccess = querySuccess; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
