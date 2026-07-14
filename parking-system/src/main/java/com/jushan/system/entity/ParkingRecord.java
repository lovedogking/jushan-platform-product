package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 停车记录实体（T30）。
 * <p>
 * 记录车辆入场及在场状态。同车同停车场通过 MySQL 8 功能唯一索引保证
 * 仅一条 PARKING 记录（{@code uk_active_parking}），提供数据库级并发保护。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>{@code tenant_id} 从停车场可信记录推导，不信任外部输入</li>
 *   <li>{@code standardized_plate} 由 {@code PlateStandardizer} 标准化后存储</li>
 *   <li>状态变更使用条件更新，禁止整实体覆盖</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("parking_record")
public class ParkingRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（从停车场推导） */
    private Long tenantId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 入场车道 ID */
    private Long laneId;

    /** 入场相机设备 ID */
    private Long deviceId;

    /** 标准化车牌号 */
    private String standardizedPlate;

    /** 关联的入场识别事件 ID（recognition_event_log.id） */
    private Long entryEventId;

    /** 状态：PARKING-在场, COMPLETED-已完成, CANCELLED-已作废 */
    private String status;

    // ==================== 常量定义 ====================

    public static final String STATUS_PARKING = "PARKING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 入场时间 */
    private LocalDateTime entryTime;

    /** 出场时间 */
    private LocalDateTime exitTime;

    /** 收费规则版本（占位，T34 实现） */
    private String feeRuleVersion;

    /** 入场抓拍图片路径（UPDATE 策略时更新为最新抓拍） */
    private String entryImagePath;

    /** 关联的出场识别事件 ID（recognition_event_log.id） */
    private Long exitEventId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 逻辑删除时间（NULL 表示未删除） */
    private LocalDateTime deletedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getStandardizedPlate() { return standardizedPlate; }
    public void setStandardizedPlate(String standardizedPlate) { this.standardizedPlate = standardizedPlate; }

    public Long getEntryEventId() { return entryEventId; }
    public void setEntryEventId(Long entryEventId) { this.entryEventId = entryEventId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public String getFeeRuleVersion() { return feeRuleVersion; }
    public void setFeeRuleVersion(String feeRuleVersion) { this.feeRuleVersion = feeRuleVersion; }

    public String getEntryImagePath() { return entryImagePath; }
    public void setEntryImagePath(String entryImagePath) { this.entryImagePath = entryImagePath; }

    public Long getExitEventId() { return exitEventId; }
    public void setExitEventId(Long exitEventId) { this.exitEventId = exitEventId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
