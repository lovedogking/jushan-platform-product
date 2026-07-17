package com.smartparking.deviceaccess.api.dto;

import lombok.Data;

/**
 * 设备更新请求体。
 * <p>
 * 所有字段均为可选 — 只更新提供的非 null 字段。
 * deviceId 和 productId 不可通过此接口修改。
 * v0.3: brand/model 移除，新增 direction。
 * v0.4: 新增 platformDeviceId / tenantId / parkingLotId / laneId（业务侧维度，不参与 Device Access 内部逻辑）。
 */
@Data
public class DeviceUpdateRequest {

    /** 设备名称 */
    private String deviceName;

    /** 设备方向：ENTRANCE / EXIT / BIDIRECTIONAL */
    private String direction;

    /** 平台设备ID（业务侧关联标识，可选） */
    private String platformDeviceId;

    /** 租户ID（业务侧维度，可选） */
    private String tenantId;

    /** 停车场ID（业务侧维度，可选） */
    private String parkingLotId;

    /** 车道ID（业务侧维度，可选） */
    private String laneId;

    /** 显示屏启用: 0-关闭, 1-启用（v0.3 显示配置持久化） */
    private Boolean displayEnabled;

    /** 显示屏模式: TWO_LINE / FOUR_LINE（v0.3 显示配置持久化） */
    private String displayMode;

    /** 备注 */
    private String remark;
}
