package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

import com.smartparking.deviceaccess.common.enums.DeviceCapability;

import java.util.List;

/**
 * 设备详情响应 DTO（含 relations）。
 * <p>
 * 用于 GET /api/v1/devices/{deviceId} 单个设备查询。
 * v0.3 新增：在 DeviceDTO 基础上增加设备关系列表。
 * v0.4: 新增 platformDeviceId / tenantId / parkingLotId / laneId（业务侧维度）。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceDetailDTO {

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

    // ── 产品字段 ──
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

    // ── 设备关系（仅详情返回） ──
    private List<DeviceRelationDTO> relations;
}
