package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通道创建请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingLaneCreateCmd {

    @NotNull(message = "所属车场不能为空")
    private Long lotId;

    private Long zoneId;

    @NotBlank(message = "通道编号不能为空")
    @Size(max = 32, message = "编号长度不能超过32")
    private String laneNo;

    @NotBlank(message = "通道名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128")
    private String name;

    @NotNull(message = "通道类型不能为空")
    private Integer type;

    private Long entryCameraId;

    private Long exitCameraId;

    private Integer status;

    private Integer tideMode;

    private Integer cameraMode;
}
