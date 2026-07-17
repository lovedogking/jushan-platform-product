package com.smartparking.deviceaccess.common.exception;

import com.smartparking.deviceaccess.common.enums.DeviceCapability;

/**
 * 设备不支持指定能力异常。
 * <p>
 * 当设备产品型号未声明某能力，但 API 层收到对应命令时抛出。
 * v0.4 新增。
 */
public class CapabilityUnsupportedException extends DeviceAccessException {

    public CapabilityUnsupportedException(String deviceId, DeviceCapability capability) {
        super(422, "Device does not support capability: " + capability + " (deviceId=" + deviceId + ")");
    }
}
