package com.smartparking.deviceaccess.common.exception;

/**
 * 设备关系已存在异常。
 * <p>
 * v0.3 新增。
 */
public class RelationAlreadyExistsException extends DeviceAccessException {

    public RelationAlreadyExistsException(String relationType, String sourceDeviceId, String targetDeviceId) {
        super(409, "Relation already exists: " + relationType + " " + sourceDeviceId + " → " + targetDeviceId);
    }
}
