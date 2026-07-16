package com.jushan.system.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通行记录管理视图 VO（Phase 2 D1）。
 * <p>
 * 展示停车记录及关联的订单费用信息。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingRecordAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录 ID */
    private Long id;

    /** 车牌号 */
    private String plateNumber;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 停车场名称 */
    private String parkingLotName;

    /** 入场通道 ID */
    private Long entryLaneId;

    /** 入场通道名称 */
    private String entryLaneName;

    /** 出口通道名称（离场时） */
    private String exitLaneName;

    /** 入场时间 */
    private LocalDateTime entryTime;

    /** 出场时间 */
    private LocalDateTime exitTime;

    /** 停车时长（分钟） */
    private Integer parkingDurationMinutes;

    /** 状态：PARKING-在场, COMPLETED-已离场, CANCELLED-已作废 */
    private String status;

    /** 状态中文 */
    private String statusLabel;

    /** 应收金额（分） */
    private Integer feeAmount;

    /** 实付金额（分） */
    private Integer paidAmount;

    /** 支付方式 */
    private String payChannel;

    /** 支付方式中文 */
    private String payChannelLabel;

    /** 操作人姓名 */
    private String operatorName;

    /** 放行原因 */
    private String releaseReason;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public Long getEntryLaneId() { return entryLaneId; }
    public void setEntryLaneId(Long entryLaneId) { this.entryLaneId = entryLaneId; }

    public String getEntryLaneName() { return entryLaneName; }
    public void setEntryLaneName(String entryLaneName) { this.entryLaneName = entryLaneName; }

    public String getExitLaneName() { return exitLaneName; }
    public void setExitLaneName(String exitLaneName) { this.exitLaneName = exitLaneName; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public Integer getParkingDurationMinutes() { return parkingDurationMinutes; }
    public void setParkingDurationMinutes(Integer parkingDurationMinutes) { this.parkingDurationMinutes = parkingDurationMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public Integer getFeeAmount() { return feeAmount; }
    public void setFeeAmount(Integer feeAmount) { this.feeAmount = feeAmount; }

    public Integer getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Integer paidAmount) { this.paidAmount = paidAmount; }

    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }

    public String getPayChannelLabel() { return payChannelLabel; }
    public void setPayChannelLabel(String payChannelLabel) { this.payChannelLabel = payChannelLabel; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getReleaseReason() { return releaseReason; }
    public void setReleaseReason(String releaseReason) { this.releaseReason = releaseReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
