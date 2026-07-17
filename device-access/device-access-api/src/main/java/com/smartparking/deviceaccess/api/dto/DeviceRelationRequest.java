package com.smartparking.deviceaccess.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建设备关系请求体。
 * <p>
 * v0.3 新增。sourceDeviceId 从 URL path 获取。
 */
@Data
public class DeviceRelationRequest {

    /** 关系类型：AUX_CAMERA / RS485_DISPLAY */
    @NotBlank(message = "relationType must not be blank")
    private String relationType;

    /** 关联目标设备ID */
    @NotBlank(message = "targetDeviceId must not be blank")
    private String targetDeviceId;

    /** 备注 */
    private String remark;
}
