package com.jushan.system.vo;

import java.util.List;

/**
 * 岗亭监控快照视图对象（P005）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BoothMonitorSnapshotVO {

    /** 停车场摘要 */
    private ParkingLotVO parkingLot;

    /** 车道列表 */
    private List<BoothLaneVO> lanes;

    /** 最近识别事件 */
    private List<BoothRecognitionEventVO> recentEvents;

    /** 设备状态列表 */
    private List<DeviceStatusVO> deviceStatuses;

    /** 未确认异常提醒 */
    private List<MonitorAlertVO> alerts;

    // ==================== getter / setter ====================

    public ParkingLotVO getParkingLot() { return parkingLot; }
    public void setParkingLot(ParkingLotVO parkingLot) { this.parkingLot = parkingLot; }

    public List<BoothLaneVO> getLanes() { return lanes; }
    public void setLanes(List<BoothLaneVO> lanes) { this.lanes = lanes; }

    public List<BoothRecognitionEventVO> getRecentEvents() { return recentEvents; }
    public void setRecentEvents(List<BoothRecognitionEventVO> recentEvents) { this.recentEvents = recentEvents; }

    public List<DeviceStatusVO> getDeviceStatuses() { return deviceStatuses; }
    public void setDeviceStatuses(List<DeviceStatusVO> deviceStatuses) { this.deviceStatuses = deviceStatuses; }

    public List<MonitorAlertVO> getAlerts() { return alerts; }
    public void setAlerts(List<MonitorAlertVO> alerts) { this.alerts = alerts; }
}
