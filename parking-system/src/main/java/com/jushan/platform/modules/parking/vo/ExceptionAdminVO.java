package com.jushan.platform.modules.parking.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异常记录管理视图 VO（Phase 2 D2）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ExceptionAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录 ID */
    private Long id;

    /** 异常类型 */
    private String exceptionType;

    /** 异常类型中文 */
    private String exceptionTypeLabel;

    /** 车牌号 */
    private String plateNumber;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 停车场名称 */
    private String parkingLotName;

    /** 通道名称 */
    private String laneName;

    /** 异常描述 */
    private String description;

    /** 处理状态：UNHANDLED-未处理, HANDLED-已处理 */
    private String status;

    /** 处理状态中文 */
    private String statusLabel;

    /** 创建时间（异常发生时间） */
    private LocalDateTime createdAt;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理人 */
    private String handlerName;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getExceptionTypeLabel() { return exceptionTypeLabel; }
    public void setExceptionTypeLabel(String exceptionTypeLabel) { this.exceptionTypeLabel = exceptionTypeLabel; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public String getLaneName() { return laneName; }
    public void setLaneName(String laneName) { this.laneName = laneName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }

    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
}
