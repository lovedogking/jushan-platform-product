package com.smartparking.deviceaccess.common.exception;

/**
 * 设备未找到异常。
 * <p>
 * 当根据 deviceId 查询设备不存在时抛出。
 */
public class DeviceNotFoundException extends DeviceAccessException {

    public DeviceNotFoundException(String deviceId) {
        super(404, "Device not found: " + deviceId);
    }
}
