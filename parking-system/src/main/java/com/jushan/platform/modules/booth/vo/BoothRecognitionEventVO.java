package com.jushan.platform.modules.booth.vo;

import java.time.LocalDateTime;

/**
 * 岗亭监控识别事件视图对象（P005）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BoothRecognitionEventVO {

    private String eventId;
    private Long logId;
    private String plateNumber;
    private String standardizedPlate;
    private String direction;
    private LocalDateTime eventTime;
    private Integer confidence;
    private String source;
    private String status;
    private Long laneId;
    private String laneName;
    private Long deviceId;
    private String deviceName;
    private String imagePath;
    private String plateImagePath;

    // ==================== getter / setter ====================

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getStandardizedPlate() { return standardizedPlate; }
    public void setStandardizedPlate(String standardizedPlate) { this.standardizedPlate = standardizedPlate; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public String getLaneName() { return laneName; }
    public void setLaneName(String laneName) { this.laneName = laneName; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getPlateImagePath() { return plateImagePath; }
    public void setPlateImagePath(String plateImagePath) { this.plateImagePath = plateImagePath; }
}
