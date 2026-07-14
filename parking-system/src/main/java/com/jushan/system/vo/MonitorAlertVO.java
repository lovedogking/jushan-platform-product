package com.jushan.system.vo;

import java.time.LocalDateTime;

/**
 * 岗亭监控异常提醒视图对象（P005）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MonitorAlertVO {

    private Long id;
    private String alertType;
    private String severity;
    private String sourceId;
    private String message;
    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
