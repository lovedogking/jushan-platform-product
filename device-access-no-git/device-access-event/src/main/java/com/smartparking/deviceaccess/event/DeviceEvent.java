package com.smartparking.deviceaccess.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 统一事件信封（品牌无关）。
 * <p>
 * 所有设备事件都包装在此信封中推送给 Parking Platform。
 * 事件信封格式遵循 ARCHITECTURE.md 8.1.2 节定义。
 * <p>
 * 字段来源：
 * <ul>
 *   <li>tenantId/parkingLotId/laneId/platformDeviceId — 从 t_device 表读取（设备注册时传入）</li>
 *   <li>deviceSn/vendor — 从设备消息/产品目录读取</li>
 *   <li>occurredAt — 设备上报时间</li>
 *   <li>receivedAt — Device Access 收到时间</li>
 *   <li>payload — 事件类型特定的载荷</li>
 * </ul>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceEvent {

    /** 事件唯一ID */
    private String eventId;

    /** 事件类型，如 "PLATE_RECOGNIZED" */
    private String eventType;

    /** 租户ID（业务侧维度，从 t_device 读取） */
    private String tenantId;

    /** 停车场ID（业务侧维度，从 t_device 读取） */
    private String parkingLotId;

    /** 车道ID（业务侧维度，从 t_device 读取） */
    private String laneId;

    /** 平台设备ID（业务侧关联标识，从 t_device 读取） */
    private String platformDeviceId;

    /** 设备序列号 */
    private String deviceSn;

    /** 设备厂商："ZHENSHI" / "XINLUTONG" */
    private String vendor;

    /** 事件发生时间（ISO-8601，设备上报时间） */
    private String occurredAt;

    /** 事件接收时间（ISO-8601，Device Access 收到时间） */
    private String receivedAt;

    /** 事件载荷，类型特定字段 */
    private Map<String, Object> payload;
}
