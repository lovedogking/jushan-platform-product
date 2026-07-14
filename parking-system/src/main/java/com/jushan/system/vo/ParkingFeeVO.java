package com.jushan.system.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 停车费用查询/结算预览响应 VO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingFeeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 停车记录 ID */
    private Long recordId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 停车场名称 */
    private String parkingLotName;

    /** 标准化车牌号 */
    private String plate;

    /** 入场时间 */
    private LocalDateTime entryTime;

    /** 实际出场时间（仅 COMPLETED 记录有值） */
    private LocalDateTime exitTime;

    /** 预览出场时间（仅 preview 接口有值） */
    private LocalDateTime previewExitTime;

    /** 记录状态：PARKING / COMPLETED / CANCELLED */
    private String status;

    /** 停车时长（分钟） */
    private Long durationMinutes;

    /** 当前计算/实际费用（分） */
    private Integer feeCents;

    /** 规则免费时长（分钟） */
    private Integer freeMinutes;

    /** 免费出场截止时间 */
    private LocalDateTime freeExitDeadline;

    /** 生效收费规则版本 ID */
    private Long ruleVersionId;

    /** 版本号展示，如 v3 */
    private String ruleVersion;

    /** 是否存在待支付订单 */
    private Boolean hasPendingOrder;

    /** 待支付订单 ID */
    private Long pendingOrderId;

    /** 待支付订单金额（分） */
    private Integer pendingOrderAmountCents;

    /** 是否可支付（仅 PARKING 状态为 true） */
    private Boolean payable;

    /** 友好提示 */
    private String message;

    // ==================== getter / setter ====================

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public LocalDateTime getPreviewExitTime() { return previewExitTime; }
    public void setPreviewExitTime(LocalDateTime previewExitTime) { this.previewExitTime = previewExitTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Long durationMinutes) { this.durationMinutes = durationMinutes; }

    public Integer getFeeCents() { return feeCents; }
    public void setFeeCents(Integer feeCents) { this.feeCents = feeCents; }

    public Integer getFreeMinutes() { return freeMinutes; }
    public void setFreeMinutes(Integer freeMinutes) { this.freeMinutes = freeMinutes; }

    public LocalDateTime getFreeExitDeadline() { return freeExitDeadline; }
    public void setFreeExitDeadline(LocalDateTime freeExitDeadline) { this.freeExitDeadline = freeExitDeadline; }

    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }

    public Boolean getHasPendingOrder() { return hasPendingOrder; }
    public void setHasPendingOrder(Boolean hasPendingOrder) { this.hasPendingOrder = hasPendingOrder; }

    public Long getPendingOrderId() { return pendingOrderId; }
    public void setPendingOrderId(Long pendingOrderId) { this.pendingOrderId = pendingOrderId; }

    public Integer getPendingOrderAmountCents() { return pendingOrderAmountCents; }
    public void setPendingOrderAmountCents(Integer pendingOrderAmountCents) { this.pendingOrderAmountCents = pendingOrderAmountCents; }

    public Boolean getPayable() { return payable; }
    public void setPayable(Boolean payable) { this.payable = payable; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
