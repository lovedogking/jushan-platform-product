package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新车道请求。
 * <p>
 * 所有字段均为可选，仅非 null 字段被更新。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class UpdateLaneRequest {

    /** 车道名称 */
    @Size(max = 128, message = "车道名称最长128个字符")
    private String name;

    /** 车道编号（停车场内唯一） */
    @Size(max = 64, message = "车道编号最长64个字符")
    private String laneNo;

    /** 通道类型：1-入口, 2-出口, 3-双向 */
    private Integer type;

    /** 控闸设备 ID（可选，显式指定本车道用哪台设备控闸） */
    private Long gateDeviceId;

    /** 入口相机 ID（可选） */
    private Long entryCameraId;

    /** 出口相机 ID（可选） */
    private Long exitCameraId;

    /** 潮汐模式（可选） */
    private Integer tideMode;

    /** 相机模式（可选） */
    private Integer cameraMode;

    /** 状态：1-启用, 2-禁用 */
    private Integer status;

    // ==================== getter / setter ====================

    public Long getGateDeviceId() { return gateDeviceId; }
    public void setGateDeviceId(Long gateDeviceId) { this.gateDeviceId = gateDeviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLaneNo() { return laneNo; }
    public void setLaneNo(String laneNo) { this.laneNo = laneNo; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Long getEntryCameraId() { return entryCameraId; }
    public void setEntryCameraId(Long entryCameraId) { this.entryCameraId = entryCameraId; }

    public Long getExitCameraId() { return exitCameraId; }
    public void setExitCameraId(Long exitCameraId) { this.exitCameraId = exitCameraId; }

    public Integer getTideMode() { return tideMode; }
    public void setTideMode(Integer tideMode) { this.tideMode = tideMode; }

    public Integer getCameraMode() { return cameraMode; }
    public void setCameraMode(Integer cameraMode) { this.cameraMode = cameraMode; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
