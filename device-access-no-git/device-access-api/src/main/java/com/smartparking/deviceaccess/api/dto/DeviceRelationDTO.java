package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 设备关系响应 DTO。
 * <p>
 * v0.3 新增。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceRelationDTO {

    /** 关系ID */
    private Long relationId;

    /** 关系类型：AUX_CAMERA / RS485_DISPLAY */
    private String relationType;

    /** 关系方向：OUTBOUND（当前设备为 source）/ INBOUND（当前设备为 target） */
    private String direction;

    // ── 关联设备摘要 ──
    private String relatedDeviceId;
    private String relatedDeviceName;
    private String relatedProductName;
    private String relatedDeviceType;
    private String relatedStatus;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private String createTime;
}
