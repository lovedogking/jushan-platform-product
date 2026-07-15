package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗亭监控异常提醒实体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("monitor_alert")
public class MonitorAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 告警类型：设备离线 */
    public static final String TYPE_DEVICE_OFFLINE = "DEVICE_OFFLINE";

    /** 告警类型：车位已满 */
    public static final String TYPE_LOT_FULL = "LOT_FULL";

    /** 告警类型：停车场停用 */
    public static final String TYPE_LOT_DISABLED = "LOT_DISABLED";

    /** 告警类型：识别失败 */
    public static final String TYPE_RECOGNITION_FAIL = "RECOGNITION_FAIL";

    /** 告警类型：超时停放（系统自动拉黑） */
    public static final String TYPE_OVERSTAY = "OVERSTAY";

    /** 告警类型：储值车余额不足 */
    public static final String TYPE_BALANCE_INSUFFICIENT = "BALANCE_INSUFFICIENT";

    /** 严重程度：警告 */
    public static final String SEVERITY_WARNING = "WARNING";

    /** 严重程度：严重 */
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 异常类型 */
    private String alertType;

    /** 严重程度 */
    private String severity;

    /** 关联来源 ID */
    private String sourceId;

    /** 异常描述 */
    private String message;

    /** 是否已确认：0-未确认, 1-已确认 */
    private Integer acknowledged;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 确认时间 */
    private LocalDateTime acknowledgedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getAcknowledged() { return acknowledged; }
    public void setAcknowledged(Integer acknowledged) { this.acknowledged = acknowledged; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
