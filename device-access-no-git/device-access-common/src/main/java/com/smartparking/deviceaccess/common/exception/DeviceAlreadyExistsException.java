package com.smartparking.deviceaccess.common.exception;

/**
 * 设备已存在异常。
 * <p>
 * 当尝试注册一个 deviceId 已被占用的设备时抛出（409 Conflict）。
 */
public class DeviceAlreadyExistsException extends DeviceAccessException {

    public DeviceAlreadyExistsException(String deviceId) {
        super(409, "Device already exists: " + deviceId);
    }
}
