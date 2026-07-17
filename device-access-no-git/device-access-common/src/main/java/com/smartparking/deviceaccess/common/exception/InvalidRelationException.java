package com.smartparking.deviceaccess.common.exception;

/**
 * 无效的设备关系异常。
 * <p>
 * 用于关联自己、设备不存在、设备类型不匹配等校验场景。
 * v0.3 新增。
 */
public class InvalidRelationException extends DeviceAccessException {

    public InvalidRelationException(String message) {
        super(400, message);
    }
}
