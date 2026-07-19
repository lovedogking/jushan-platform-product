package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通道管理视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingLaneVO {

    private Long id;
    private Long tenantId;
    private Long lotId;
    private Long zoneId;
    private String laneNo;
    private String name;
    private Integer type;
    private Long entryCameraId;
    private Long exitCameraId;
    private Integer status;
    private Integer tideMode;
    private Integer cameraMode;
    private Integer version;
    private String gateMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
