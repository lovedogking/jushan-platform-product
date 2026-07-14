package com.jushan.system.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台内部标准识别事件载荷（T28）。
 * <p>
 * 这是平台内部统一的车辆识别事件模型，所有来源（人工、Mock、未来 Device Access）
 * 的识别事件在进入平台业务处理前，都必须转换为本结构。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>{@code tenantId} 和 {@code parkingLotId} 由后端从可信设备/车道记录推导，
 *       禁止信任外部输入</li>
 *   <li>{@code deviceId} 是平台设备主键，不是厂商 SN</li>
 *   <li>{@code eventId} 用于业务层幂等判定（T29 实现）</li>
 * </ul>
 * <p>
 * <strong>字段说明</strong>：
 * <ul>
 *   <li>eventId — 事件唯一 ID（UUID v4），用于幂等</li>
 *   <li>deviceId — 平台设备主键（相机设备）</li>
 *   <li>laneId — 车道 ID</li>
 *   <li>parkingLotId — 停车场 ID（由后端推导）</li>
 *   <li>tenantId — 租户 ID（由后端推导）</li>
 *   <li>plateNumber — 车牌号</li>
 *   <li>direction — 方向：ENTRY（入场）/ EXIT（出场）</li>
 *   <li>eventTime — 事件发生时间</li>
 *   <li>imagePath — 全景图路径占位（当前为 Mock 值或空）</li>
 *   <li>plateImagePath — 车牌特写图路径占位（当前为 Mock 值或空）</li>
 *   <li>confidence — 识别置信度 0-100（可选）</li>
 *   <li>source — 事件来源（MANUAL / MOCK / DEVICE_ACCESS）</li>
 *   <li>rawData — 原始数据摘要（调试用，生产可裁剪）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecognitionEventPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件唯一 ID（UUID v4），用于业务层幂等 */
    private String eventId;

    /** 平台设备主键（相机设备） */
    private Long deviceId;

    /** 车道 ID */
    private Long laneId;

    /** 停车场 ID（后端推导，禁止信任外部输入） */
    private Long parkingLotId;

    /** 租户 ID（后端推导，禁止信任外部输入） */
    private Long tenantId;

    /** 车牌号 */
    private String plateNumber;

    /** 方向：ENTRY-入场, EXIT-出场 */
    private String direction;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 全景图路径占位 */
    private String imagePath;

    /** 车牌特写图路径占位 */
    private String plateImagePath;

    /** 识别置信度 0-100（可选） */
    private Integer confidence;

    /** 事件来源 */
    private String source;

    /** 原始数据摘要（调试用） */
    private String rawData;

    /** 厂商原始事件ID（Device Access 对接时使用，当前为 null） */
    private String vendorEventId;

    /** 平台内部识别事件日志主键（recognition_event_log.id），用于业务关联 */
    private Long logId;

    // ==================== 工厂方法 ====================

    /**
     * 创建一个识别事件载荷。
     *
     * @param eventId     事件唯一 ID
     * @param plateNumber 车牌号
     * @param direction   方向（ENTRY / EXIT）
     * @param source      事件来源
     * @return 预填充 eventTime 的载荷
     */
    public static RecognitionEventPayload of(String eventId, String plateNumber,
                                              String direction, EventSource source) {
        RecognitionEventPayload payload = new RecognitionEventPayload();
        payload.eventId = eventId;
        payload.plateNumber = plateNumber;
        payload.direction = direction;
        payload.source = source.name();
        payload.eventTime = LocalDateTime.now();
        return payload;
    }

    // ==================== Fluent 方法 ====================

    public RecognitionEventPayload deviceId(Long deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    public RecognitionEventPayload laneId(Long laneId) {
        this.laneId = laneId;
        return this;
    }

    public RecognitionEventPayload parkingLotId(Long parkingLotId) {
        this.parkingLotId = parkingLotId;
        return this;
    }

    public RecognitionEventPayload tenantId(Long tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public RecognitionEventPayload imagePath(String imagePath) {
        this.imagePath = imagePath;
        return this;
    }

    public RecognitionEventPayload plateImagePath(String plateImagePath) {
        this.plateImagePath = plateImagePath;
        return this;
    }

    public RecognitionEventPayload confidence(Integer confidence) {
        this.confidence = confidence;
        return this;
    }

    public RecognitionEventPayload rawData(String rawData) {
        this.rawData = rawData;
        return this;
    }

    public RecognitionEventPayload vendorEventId(String vendorEventId) {
        this.vendorEventId = vendorEventId;
        return this;
    }

    public RecognitionEventPayload logId(Long logId) {
        this.logId = logId;
        return this;
    }

    public RecognitionEventPayload eventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
        return this;
    }

    // ==================== getter / setter ====================

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getPlateImagePath() { return plateImagePath; }
    public void setPlateImagePath(String plateImagePath) { this.plateImagePath = plateImagePath; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public String getVendorEventId() { return vendorEventId; }
    public void setVendorEventId(String vendorEventId) { this.vendorEventId = vendorEventId; }

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    @Override
    public String toString() {
        return "RecognitionEventPayload{eventId='" + eventId + "', deviceId=" + deviceId
                + ", laneId=" + laneId + ", parkingLotId=" + parkingLotId
                + ", tenantId=" + tenantId + ", plateNumber='" + plateNumber
                + "', direction=" + direction + ", eventTime=" + eventTime
                + ", source=" + source + "}";
    }
}
