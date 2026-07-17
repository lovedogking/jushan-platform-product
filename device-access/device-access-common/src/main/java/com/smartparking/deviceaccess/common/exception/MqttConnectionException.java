package com.smartparking.deviceaccess.common.exception;

/**
 * MQTT 连接异常。
 * <p>
 * 当 MQTT Broker 不可达、认证失败或连接意外断开时抛出。
 */
public class MqttConnectionException extends DeviceAccessException {

    public MqttConnectionException(String message) {
        super(503, message);
    }

    public MqttConnectionException(String message, Throwable cause) {
        super(503, message, cause);
    }
}
