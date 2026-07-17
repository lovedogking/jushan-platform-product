package com.smartparking.deviceaccess.mqtt;

import com.smartparking.deviceaccess.common.dto.mqtt.MqttMessage;

/**
 * MQTT 消息监听器。
 * <p>
 * 由上层模块（adapter）实现，MQTT 模块收到消息后直接回调，不解析业务数据。
 */
@FunctionalInterface
public interface MqttMessageListener {

    /**
     * 收到 MQTT 消息时回调。
     *
     * @param topic   消息来源 Topic
     * @param message 已反序列化的通用消息信封
     */
    void onMessage(String topic, MqttMessage message);
}
