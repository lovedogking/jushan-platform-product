package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 车道实体（T19 入口、出口与车道模型）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("parking_lane")
public class ParkingLane implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属停车场 ID */
    private Long parkingLotId;

    /** 车道名称 */
    private String name;

    /** 车道编码（停车场内唯一） */
    private String code;

    /** 车道方向：ENTRY-入口, EXIT-出口, MIXED-混合 */
    private String direction;

    /** 状态：ENABLED-启用, DISABLED-停用 */
    private String status;

    /** 是否为关键车道：1-是, 0-否（关键车道离线可能导致停车场不可用） */
    private Integer isKeyLane;

    /** 自动放行策略：AUTO-自动放行, MANUAL-人工确认, AFTER_PAY-缴费后自动放行 */
    private String autoReleasePolicy;

    /** 备注 */
    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getIsKeyLane() { return isKeyLane; }
    public void setIsKeyLane(Integer isKeyLane) { this.isKeyLane = isKeyLane; }

    public String getAutoReleasePolicy() { return autoReleasePolicy; }
    public void setAutoReleasePolicy(String autoReleasePolicy) { this.autoReleasePolicy = autoReleasePolicy; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
