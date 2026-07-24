package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * Mock/人工识别事件触发请求（T28）。
 * <p>
 * 仅用于 local/test 环境的 Mock 或人工触发识别事件。
 * <strong>生产环境此接口不可用</strong>。
 * <p>
 * 调用方只需提供平台设备引用、车牌和方向。tenantId、parkingLotId、laneId
 * 等数据范围字段由后端从可信设备记录推导，不可通过本请求传入。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MockRecognitionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平台设备主键（相机设备，必填） */
    @NotNull(message = "设备 ID 不能为空")
    private Long deviceId;

    /** 车牌号（必填） */
    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    /** 方向（必填）：ENTRY-入场, EXIT-出场 */
    @NotBlank(message = "方向不能为空")
    private String direction;

    /** 识别置信度 0-100（可选） */
    private Integer confidence;

    /** 全景图路径占位（可选） */
    private String imagePath;

    /** 车牌特写图路径占位（可选） */
    private String plateImagePath;

    // ==================== getter / setter ====================

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getPlateImagePath() { return plateImagePath; }
    public void setPlateImagePath(String plateImagePath) { this.plateImagePath = plateImagePath; }
}
