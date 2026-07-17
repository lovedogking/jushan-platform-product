package com.jushan.system.vo;

import java.util.List;

/**
 * 岗亭监控车道视图对象（P005）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BoothLaneVO {

    private Long id;
    private Long parkingLotId;
    private String name;
    private String code;
    private String direction;
    private String status;
    private Long deviceId;
    private String deviceName;
    /** 车道绑定的相机列表（任务包 3-5：支持多相机/主备场景） */
    private List<BoothLaneCameraVO> cameras;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public List<BoothLaneCameraVO> getCameras() { return cameras; }
    public void setCameras(List<BoothLaneCameraVO> cameras) { this.cameras = cameras; }
}
