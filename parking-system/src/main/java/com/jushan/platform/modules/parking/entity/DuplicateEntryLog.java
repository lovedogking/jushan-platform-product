package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异常重复入场记录实体（P003）。
 * <p>
 * 记录同一车辆在已有未结停车记录时再次触发入口识别的处理过程。
 * 供运营人员查看和处置。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("duplicate_entry_log")
public class DuplicateEntryLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 车道 ID */
    private Long laneId;

    /** 设备 ID */
    private Long deviceId;

    /** 标准化车牌号 */
    private String standardizedPlate;

    /** 关联的已有停车记录 ID */
    private Long existingRecordId;

    /** 执行策略：REJECT/UPDATE/EXCEPTION */
    private String strategy;

    /** 执行动作：REJECTED-已拒绝, UPDATED-已更新, EXCEPTION_CREATED-已创建异常 */
    private String action;

    /** 处置说明/原因 */
    private String reason;

    /** 关联的入场识别事件 ID */
    private Long entryEventId;

    /** 本次抓拍图片路径 */
    private String imagePath;

    /** 识别置信度 */
    private Integer confidence;

    private LocalDateTime createdAt;

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

    public Long getExistingRecordId() { return existingRecordId; }
    public void setExistingRecordId(Long existingRecordId) { this.existingRecordId = existingRecordId; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getEntryEventId() { return entryEventId; }
    public void setEntryEventId(Long entryEventId) { this.entryEventId = entryEventId; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
