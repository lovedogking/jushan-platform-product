package com.jushan.system.dto;

/**
 * 远程开闸 WebSocket 推送 DTO（Phase 1 B2）。
 * <p>
 * 运营端远程开闸后，通过 WebSocket 推送到岗亭端。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class RemoteGateAlertDTO {

    /** 消息类型标识，固定为 REMOTE_GATE_OPEN */
    private String type = "REMOTE_GATE_OPEN";

    /** 操作人员姓名 */
    private String operatorName;

    /** 操作时间（yyyy-MM-dd HH:mm:ss） */
    private String operationTime;

    /** 车场名称 */
    private String parkingLotName;

    /** 通道名称 */
    private String laneName;

    /** 开闸原因 */
    private String reason;

    /** 岗亭端弹窗自动消失秒数 */
    private int autoDismissSeconds = 10;

    public RemoteGateAlertDTO() {}

    public String getType() { return type; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getOperationTime() { return operationTime; }
    public void setOperationTime(String operationTime) { this.operationTime = operationTime; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public String getLaneName() { return laneName; }
    public void setLaneName(String laneName) { this.laneName = laneName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getAutoDismissSeconds() { return autoDismissSeconds; }
    public void setAutoDismissSeconds(int autoDismissSeconds) { this.autoDismissSeconds = autoDismissSeconds; }
}
