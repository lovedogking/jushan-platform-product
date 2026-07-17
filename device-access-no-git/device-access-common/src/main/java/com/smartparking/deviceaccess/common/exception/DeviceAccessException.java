package com.smartparking.deviceaccess.common.exception;

import lombok.Getter;

/**
 * Device Access 模块统一业务异常基类。
 * <p>
 * 所有模块内部的业务异常均继承此类，方便上层统一处理。
 */
@Getter
public class DeviceAccessException extends RuntimeException {

    private final int code;

    public DeviceAccessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public DeviceAccessException(String message) {
        this(500, message);
    }

    public DeviceAccessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DeviceAccessException(String message, Throwable cause) {
        this(500, message, cause);
    }
}
