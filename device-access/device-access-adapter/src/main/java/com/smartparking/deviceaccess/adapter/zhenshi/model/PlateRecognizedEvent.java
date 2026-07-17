package com.smartparking.deviceaccess.adapter.zhenshi.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 车牌识别结果事件。
 * <p>
 * 设备抓拍并识别到车牌后上报，包含完整识别信息及图片路径。
 * Topic: device/{sn}/message/up/ivs_result
 * <p>
 * 文档依据：《自定义MQTT协议文档 v1.1.14》第6.1章 接收识别结果
 */
@Getter
public class PlateRecognizedEvent extends ZhenshiEvent {

    // ── 设备信息 ──
    private final String deviceName;
    private final String ipAddr;
    private final Integer channel;
    private final Integer ruleId;

    // ── 车牌信息 ──
    /** 车牌号（UTF-8） */
    private final String license;
    /** 车牌颜色编号 */
    private final Integer plateColor;
    /** 车牌类型编号 */
    private final Integer plateType;
    /** 识别置信度 (0-100) */
    private final Integer confidence;
    /** 行驶方向：0未知 1左 2右 3上 4下 */
    private final Integer direction;

    // ── 车辆信息 ──
    /** 车辆颜色编号 */
    private final Integer carColor;
    /** 车辆品牌编号 */
    private final Integer carBrand;
    /** 是否为危险车牌 */
    private final Integer isDanger;

    // ── 触发信息 ──
    /** 触发类型 */
    private final Integer triggerType;
    /** 是否为离线记录 */
    private final Integer isOffline;

    // ── 图片路径 ──
    /** 全景图路径 */
    private final String imagePath;
    /** 车牌小图路径 */
    private final String plateImagePath;

    // ── 时间 ──
    /** 抓拍时间戳（毫秒） */
    private final Long startTime;

    @Builder
    public PlateRecognizedEvent(String sn, Long eventTimestamp,
                                String deviceName, String ipAddr, Integer channel, Integer ruleId,
                                String license, Integer plateColor, Integer plateType,
                                Integer confidence, Integer direction,
                                Integer carColor, Integer carBrand, Integer isDanger,
                                Integer triggerType, Integer isOffline,
                                String imagePath, String plateImagePath,
                                Long startTime) {
        super(sn, "ivs_result", eventTimestamp);
        this.deviceName = deviceName;
        this.ipAddr = ipAddr;
        this.channel = channel;
        this.ruleId = ruleId;
        this.license = license;
        this.plateColor = plateColor;
        this.plateType = plateType;
        this.confidence = confidence;
        this.direction = direction;
        this.carColor = carColor;
        this.carBrand = carBrand;
        this.isDanger = isDanger;
        this.triggerType = triggerType;
        this.isOffline = isOffline;
        this.imagePath = imagePath;
        this.plateImagePath = plateImagePath;
        this.startTime = startTime;
    }
}
