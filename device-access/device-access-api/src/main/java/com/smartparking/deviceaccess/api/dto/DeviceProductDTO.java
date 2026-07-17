package com.smartparking.deviceaccess.api.dto;

import lombok.Builder;
import lombok.Data;

import com.smartparking.deviceaccess.common.enums.DeviceCapability;

import java.util.List;

/**
 * 设备产品目录响应 DTO。
 * <p>
 * v0.3 新增。
 */
@Data
@Builder
public class DeviceProductDTO {

    private Long id;
    private String brand;
    private String model;
    private String productName;
    private String deviceType;
    private String protocol;

    /** 设备能力列表 */
    private List<DeviceCapability> capabilities;

}
