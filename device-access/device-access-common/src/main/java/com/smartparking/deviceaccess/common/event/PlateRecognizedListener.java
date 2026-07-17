package com.smartparking.deviceaccess.common.event;

/**
 * 车牌识别事件回调接口。
 * <p>
 * 定义在 common 模块，使 adapter（依赖 common）可以调用，
 * 而 api 模块（也依赖 common）可以实现此接口，完成事件信封填充和 Webhook 推送。
 * <p>
 * 调用方：各品牌 Adapter 的 MessageHandler
 * 实现方：api 模块的 PlateRecognizedEventDispatcher
 */
public interface PlateRecognizedListener {

    /**
     * 设备识别到车牌时回调。
     *
     * @param data 车牌识别数据（品牌无关）
     */
    void onPlateRecognized(PlateRecognizedData data);
}
