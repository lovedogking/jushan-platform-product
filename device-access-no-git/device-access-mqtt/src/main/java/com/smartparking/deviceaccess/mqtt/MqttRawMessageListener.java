package com.smartparking.deviceaccess.mqtt;

import java.util.Map;

/**
 * MQTT 原始消息监听器（Map 形式）。
 * <p>
 * 与 {@link MqttMessageListener} 的区别：此接口接收未反序列化的 Map，
 * 由各 Adapter 自行解析为品牌专用消息模型。
 * <p>
 * 用于信路通等不使用臻识 MqttMessage 信封格式的品牌。
 * v0.4 新增。
 */
@FunctionalInterface
public interface MqttRawMessageListener {

    /**
     * 收到 MQTT 消息时回调。
     *
     * @param topic   消息来源 Topic
     * @param rawJson 原始 JSON 解析为 Map（key 为 String，value 可能为 String/Number/Map/List/null）
     */
    void onRawMessage(String topic, Map<String, Object> rawJson);
}
