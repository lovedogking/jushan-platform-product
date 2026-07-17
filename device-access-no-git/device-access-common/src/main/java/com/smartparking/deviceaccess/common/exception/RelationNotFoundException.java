package com.smartparking.deviceaccess.common.exception;

/**
 * 设备关系不存在异常。
 * <p>
 * v0.3 新增。
 */
public class RelationNotFoundException extends DeviceAccessException {

    public RelationNotFoundException(Long relationId) {
        super(404, "Relation not found: " + relationId);
    }
}
