package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建车道请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CreateLaneRequest {

    /** 所属停车场 ID */
    @NotNull(message = "所属停车场不能为空")
    private Long lotId;

    /** 车道名称 */
    @NotBlank(message = "车道名称不能为空")
    @Size(max = 128, message = "车道名称最长128个字符")
    private String name;

    /** 车道编号（停车场内唯一） */
    @NotBlank(message = "车道编号不能为空")
    @Size(max = 64, message = "车道编号最长64个字符")
    private String laneNo;

    /** 通道类型：1-入口, 2-出口, 3-双向 */
    @NotNull(message = "通道类型不能为空")
    private Integer type;

    /** 入口相机 ID（可选） */
    private Long entryCameraId;

    /** 出口相机 ID（可选） */
    private Long exitCameraId;

    /** 潮汐模式（可选，双向通道时有效） */
    private Integer tideMode;

    /** 相机模式（可选，双向通道时有效） */
    private Integer cameraMode;

    /** 状态：1-启用, 2-禁用 */
    private Integer status;

    // ==================== getter / setter ====================

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }

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
