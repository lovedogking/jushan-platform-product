package com.smartparking.deviceaccess.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备注册请求体。
 * <p>
 * v0.3: brand/model 替换为 productId，新增 direction。
 */
@Data
public class DeviceRegisterRequest {

    /** 设备序列号（唯一标识，如 b30113ab-a034d147） */
    @NotBlank(message = "deviceId must not be blank")
    private String deviceId;

    /** 设备名称（如"东门入口摄像头"） */
    @NotBlank(message = "deviceName must not be blank")
    private String deviceName;

    /** 产品ID，FK → t_device_product.id */
    @NotNull(message = "productId must not be null")
    private Long productId;

    /** 设备安装方向：ENTRANCE / EXIT / BIDIRECTIONAL（可选） */
    private String direction;

    /** 平台设备ID（业务侧关联标识，可选） */
    private String platformDeviceId;

    /** 租户ID（业务侧维度，可选） */
    private String tenantId;

    /** 停车场ID（业务侧维度，可选） */
    private String parkingLotId;

    /** 车道ID（业务侧维度，可选） */
    private String laneId;

    /** 备注 */
    private String remark;
}
