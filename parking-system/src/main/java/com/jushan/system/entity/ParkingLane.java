package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 车道实体（对齐 DB parking_lane 表结构）。
 * <p>
 * 字段类型约定：
 * <ul>
 *   <li>{@code type} — 1=入口(ENTRY), 2=出口(EXIT), 3=双向(MIXED)</li>
 *   <li>{@code status} — 1=启用(ENABLED), 2=禁用(DISABLED), 3=维护中(MAINTENANCE)</li>
 *   <li>前端 API 使用 String 枚举，Service 层负责转换</li>
 * </ul>
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

    /** 所属车场 ID */
    private Long lotId;

    /** 所属区域 ID */
    private Long zoneId;

    /** 通道编号（停车场内唯一），如 A1、17 */
    private String laneNo;

    /** 通道名称，如东大门 */
    private String name;

    /** 通道类型：1-入口, 2-出口, 3-双向 */
    private Integer type;

    /** 入口相机 ID（逻辑外键：device.id） */
    private Long entryCameraId;

    /** 出口相机 ID（逻辑外键：device.id） */
    private Long exitCameraId;

    /** 状态：1-启用, 2-禁用, 3-维护中 */
    private Integer status;

    /** 潮汐模式：0-关闭, 1-早高峰入口, 2-晚高峰出口 */
    private Integer tideMode;

    /** 相机配置模式：1-单相机, 2-双相机, 3-主从相机 */
    private Integer cameraMode;

    /** 闸机模式：AUTO-自动, ALWAYS_OPEN-常开, ALWAYS_CLOSE-常关 */
    private String gateMode;

    /** 乐观锁版本号 */
    private Integer version;

    /** 软删除时间 */
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // gate_mode 常量
    public static final String GATE_MODE_AUTO = "AUTO";
    public static final String GATE_MODE_ALWAYS_OPEN = "ALWAYS_OPEN";
    public static final String GATE_MODE_ALWAYS_CLOSE = "ALWAYS_CLOSE";

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public String getLaneNo() { return laneNo; }
    public void setLaneNo(String laneNo) { this.laneNo = laneNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Long getEntryCameraId() { return entryCameraId; }
    public void setEntryCameraId(Long entryCameraId) { this.entryCameraId = entryCameraId; }

    public Long getExitCameraId() { return exitCameraId; }
    public void setExitCameraId(Long exitCameraId) { this.exitCameraId = exitCameraId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getTideMode() { return tideMode; }
    public void setTideMode(Integer tideMode) { this.tideMode = tideMode; }

    public Integer getCameraMode() { return cameraMode; }
    public void setCameraMode(Integer cameraMode) { this.cameraMode = cameraMode; }

    public String getGateMode() { return gateMode; }
    public void setGateMode(String gateMode) { this.gateMode = gateMode; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
