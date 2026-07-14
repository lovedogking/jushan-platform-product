package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通道更新请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingLaneUpdateCmd {

    private Long lotId;

    private Long zoneId;

    @Size(max = 32, message = "编号长度不能超过32")
    private String laneNo;

    @Size(max = 128, message = "名称长度不能超过128")
    private String name;

    private Integer type;

    private Long entryCameraId;

    private Long exitCameraId;

    private Integer status;

    private Integer tideMode;

    private Integer cameraMode;
}
