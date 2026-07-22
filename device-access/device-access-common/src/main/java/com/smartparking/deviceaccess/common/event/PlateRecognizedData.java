package com.smartparking.deviceaccess.common.event;

/**
 * 车牌识别数据载体（品牌无关）。
 * <p>
 * 由各品牌 Adapter 在解析到车牌识别结果后构造，
 * 通过 {@link PlateRecognizedListener} 回调传递给上层（API 模块）。
 * <p>
 * 只包含设备侧能提供的原始数据，业务字段（tenantId/parkingLotId 等）
 * 由上层查 DeviceRegistry 填充。
 *
 * @param deviceSn       设备序列号
 * @param license        车牌号（UTF-8）
 * @param confidence     识别置信度 (0-100)，可能为 null
 * @param direction      行驶方向编号，可能为 null
 * @param plateColor     车牌颜色编号，可能为 null
 * @param imagePath      全景图路径/URL，可能为 null
 * @param plateImagePath 车牌特写图路径/URL，可能为 null
 * @param occurredAtMillis 设备上报时间戳（毫秒），可能为 null
 */
public record PlateRecognizedData(
        String deviceSn,
        String license,
        Integer confidence,
        Integer direction,
        Integer plateColor,
        String imagePath,
        String plateImagePath,
        Long occurredAtMillis
) {
}
