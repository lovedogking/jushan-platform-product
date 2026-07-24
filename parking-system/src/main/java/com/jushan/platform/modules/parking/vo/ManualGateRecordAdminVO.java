package com.jushan.platform.modules.parking.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 手动开闸记录 VO（Phase 2 D3）。
 * <p>
 * 展示设备命令审计中的人工开闸操作记录。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ManualGateRecordAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审计记录 ID */
    private Long id;

    /** 操作人名称 */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime operationTime;

    /** 车场 ID */
    private Long parkingLotId;

    /** 车场名称 */
    private String parkingLotName;

    /** 通道名称 */
    private String laneName;

    /** 放行原因 */
    private String reason;

    /** 关联车牌号 */
    private String plateNumber;

    /** 费用（分） */
    private Integer feeCents;

    /** 命令状态 */
    private String commandStatus;

    /** 来源：MANUAL-人工, SYSTEM-系统, AUTO_EXIT-自动出场, COMPENSATION-补偿 */
    private String source;

    /** 命令类型 */
    private String commandType;

    /** 创建时间 */
    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public LocalDateTime getOperationTime() { return operationTime; }
    public void setOperationTime(LocalDateTime operationTime) { this.operationTime = operationTime; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public String getLaneName() { return laneName; }
    public void setLaneName(String laneName) { this.laneName = laneName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Integer getFeeCents() { return feeCents; }
    public void setFeeCents(Integer feeCents) { this.feeCents = feeCents; }

    public String getCommandStatus() { return commandStatus; }
    public void setCommandStatus(String commandStatus) { this.commandStatus = commandStatus; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
