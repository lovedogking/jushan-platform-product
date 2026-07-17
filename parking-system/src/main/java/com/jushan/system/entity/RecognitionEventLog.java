package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 识别事件日志实体（T28）。
 * <p>
 * 记录平台内部所有识别事件（Mock、人工触发、未来 Device Access），
 * 提供事件追溯和审计基础。每条事件包含来源标识，可区分不同来源。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>tenant_id 和 parking_lot_id 由后端从可信设备记录推导，不信任外部输入</li>
 *   <li>device_id 是平台设备主键，不是厂商 SN</li>
 *   <li>T29 将在 event_id 上添加唯一约束以实现业务幂等</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("recognition_event_log")
public class RecognitionEventLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一 ID（UUID v4） */
    private String eventId;

    /** 租户 ID（后端推导） */
    private Long tenantId;

    /** 停车场 ID（后端推导） */
    private Long parkingLotId;

    /** 车道 ID */
    private Long laneId;

    /** 平台设备主键（相机设备） */
    private Long deviceId;

    /** 车牌号 */
    private String plateNumber;

    /** 方向：ENTRY-入场, EXIT-出场 */
    private String direction;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 识别置信度 0-100 */
    private Integer confidence;

    /** 全景图路径占位 */
    private String imagePath;

    /** 车牌特写图路径占位 */
    private String plateImagePath;

    /** 事件来源：MANUAL / MOCK / DEVICE_ACCESS */
    private String source;

    /** 原始数据摘要（调试用） */
    private String rawData;

    /** 处理状态：RECEIVED / PROCESSING / PROCESSED / FAILED */
    private String status;

    /** 失败原因（校验失败、设备不存在等） */
    private String failureReason;

    /** 标准化车牌号（去空格、统一大写后） */
    private String standardizedPlate;

    /** 厂商原始事件ID（Device Access 对接时使用） */
    private String vendorEventId;

    /** 临时车牌标记：0=正式车牌, 1=临时车牌 */
    private Integer tempPlateFlag;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置 */
    private String cameraSource;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getPlateImagePath() { return plateImagePath; }
    public void setPlateImagePath(String plateImagePath) { this.plateImagePath = plateImagePath; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getStandardizedPlate() { return standardizedPlate; }
    public void setStandardizedPlate(String standardizedPlate) { this.standardizedPlate = standardizedPlate; }

    public String getVendorEventId() { return vendorEventId; }
    public void setVendorEventId(String vendorEventId) { this.vendorEventId = vendorEventId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getTempPlateFlag() { return tempPlateFlag; }
    public void setTempPlateFlag(Integer tempPlateFlag) { this.tempPlateFlag = tempPlateFlag; }

    public String getCameraSource() { return cameraSource; }
    public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }
}
