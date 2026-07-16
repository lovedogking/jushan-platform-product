package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模拟支付车场配置。
 * <p>
 * 每个车场独立配置支付超时时间和启用状态。
 * 生产环境禁止连接真实支付平台，统一使用模拟支付流程。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("mock_payment_config")
public class MockPaymentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 雪花ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 车场ID */
    private Long parkingLotId;

    /** 支付超时时间（分钟），默认15分钟 */
    private Integer timeoutMinutes;

    /** 是否启用模拟支付 */
    private Boolean enabled;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Integer getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
