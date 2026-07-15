package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 车牌识别事件命令。
 * <p>
 * 模拟相机识别事件上报，用于云岗亭处理。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class RecognitionEventCmd {

    /** 停车场ID */
    @NotNull(message = "停车场ID不能为空")
    private Long parkingLotId;

    /** 通道ID */
    @NotNull(message = "通道ID不能为空")
    private Long laneId;

    /** 车牌号 */
    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 识别方向：ENTRY-入场, EXIT-出场 */
    @NotBlank(message = "方向不能为空")
    private String direction;

    /** 抓拍图片URL */
    @Size(max = 255, message = "图片URL最多255个字符")
    private String captureImage;

    /** 置信度（0-100） */
    private Integer confidence;

    /** 设备序列号（Webhook 场景：来自 Device Access 推送，经平台台账验证） */
    private String deviceSn;

    /** 事件 ID（Webhook 场景：来自 Device Access 推送的 eventId，用于全链路追踪） */
    private String eventId;

    /** 抓拍时间（Webhook 场景：来自 Device Access 推送的 captureTime） */
    private String captureTime;
}
