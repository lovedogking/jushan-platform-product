package com.jushan.system.vo;

import java.time.LocalDateTime;

/**
 * 车道视图对象。
 * <p>
 * 字段直接对应 DB 列，不做 String 枚举转换，
 * 前端负责根据常量映射表渲染中文标签。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingLaneVO {

    private Long id;
    private Long tenantId;
    private Long parkingLotId;
    private Long zoneId;
    private String laneNo;
    private String name;
    /** 通道类型：1=入口, 2=出口, 3=双向 */
    private Integer type;
    /** 入口相机 ID（逻辑外键：device.id） */
    private Long entryCameraId;
    /** 出口相机 ID（逻辑外键：device.id） */
    private Long exitCameraId;
    /** 状态：1=启用, 2=禁用, 3=维护中 */
    private Integer status;
    /** 潮汐模式：0=关闭, 1=早高峰入口, 2=晚高峰出口 */
    private Integer tideMode;
    /** 相机配置模式：1=单相机, 2=双相机, 3=主从相机 */
    private Integer cameraMode;
    /** 乐观锁版本号 */
    private Integer version;
    /** 闸机模式: AUTO/ALWAYS_OPEN/ALWAYS_CLOSE */
    private String gateMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
