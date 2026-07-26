package com.jushan.platform.modules.device.dto;

import lombok.Data;

/**
 * Device Access Webhook 推送事件 DTO。
 * <p>
 * 字段映射 Device Access v0.4 Webhook 推送格式（PlateRecognizedEvent）。
 * <b>安全红线</b>：{@code tenantId}、{@code parkingLotId}、{@code laneId}
 * 来自 Device Access 推送，<b>不可信任</b>，仅用于日志记录。
 * 实际租户/停车场/车道信息必须从平台设备台账推导。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class DeviceWebhookEvent {

    /** 事件唯一 ID（Device Access 生成），用于幂等去重 */
    private String eventId;

    /** 事件类型，当前固定为 PLATE_RECOGNIZED */
    private String eventType;

    /** 设备序列号（可信——平台从台账验证后使用） */
    private String deviceSn;

    /** 车牌号 */
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 识别置信度（0.0 ~ 1.0 或 0 ~ 100） */
    private Double confidence;

    /** 抓拍时间（ISO 8601 或 yyyy-MM-dd HH:mm:ss） */
    private String captureTime;

    /** 抓拍图片 URL */
    private String imageUrl;

    /** 车身颜色编号（臻识协议 0-12/255未知） */
    private Integer bodyColor;

    /** 车标品牌 */
    private String carLogo;

    /** 车牌特写图 URL */
    private String plateImageUrl;

    /** 识别方向：ENTRY-入场, EXIT-出场 */
    private String direction;

    /** ⚠️ 不可信任：Device Access 推送的租户 ID，仅用于日志 */
    private Long tenantId;

    /** ⚠️ 不可信任：Device Access 推送的停车场 ID，仅用于日志 */
    private Long parkingLotId;

    /** ⚠️ 不可信任：Device Access 推送的车道 ID，仅用于日志 */
    private Long laneId;
}
