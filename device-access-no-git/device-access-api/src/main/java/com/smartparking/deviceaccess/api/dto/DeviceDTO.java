package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import com.smartparking.deviceaccess.common.enums.DeviceCapability;

import java.util.List;

/**
 * 设备列表响应 DTO（不含 relations）。
 * <p>
 * 用于 GET 设备列表和 POST/PUT 响应。
 * v0.3: brand/model 扁平化为 product 字段，不含设备关系。
 * v0.4: 新增 platformDeviceId / tenantId / parkingLotId / laneId（业务侧维度）。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceDTO {

    // ── 设备字段 ──
    private String deviceId;
    private String deviceName;
    private String direction;
    private String platformDeviceId;
    private String tenantId;
    private String parkingLotId;
    private String laneId;
    private Boolean displayEnabled;
    private String displayMode;
    private String status;
    private String remark;

    // ── 产品字段（扁平化，从 DeviceProduct 填充） ──
    private Long productId;
    private String brand;
    private String model;
    private String productName;
    private String deviceType;
    private String protocol;

    /** 设备能力列表（从 DeviceProduct.capabilities 解析） */
    private List<DeviceCapability> capabilities;

    // ── 时间 ──
    private String lastOnlineTime;
    private String createTime;
    private String updateTime;
}
