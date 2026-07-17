package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 设备状态响应 DTO。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceStatusDTO {

    /** 设备唯一标识 */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 品牌 */
    private String brand;

    /** 型号 */
    private String model;

    /** 是否在线（基于心跳） */
    private Boolean online;

    /** 最后在线时间 */
    private String lastOnlineTime;
}
