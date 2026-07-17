package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 设备健康状态响应 DTO。
 * <p>
 * 用于 GET /api/v1/devices/{deviceId}/health 端点。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceHealthDTO {

    /** 设备唯一标识 */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 品牌 */
    private String brand;

    /** 型号 */
    private String model;

    /** 当前状态（ONLINE / OFFLINE） */
    private String status;

    /** 是否健康（心跳未超时） */
    private Boolean healthy;

    /** 最后在线时间 */
    private String lastOnlineTime;

    /** 心跳超时阈值（秒） */
    private Long heartbeatTimeoutSeconds;
}
