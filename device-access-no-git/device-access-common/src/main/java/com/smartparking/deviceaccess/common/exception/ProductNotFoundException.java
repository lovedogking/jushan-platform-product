package com.smartparking.deviceaccess.common.exception;

/**
 * 产品不存在异常。
 * <p>
 * v0.3 新增。
 */
public class ProductNotFoundException extends DeviceAccessException {

    public ProductNotFoundException(Long productId) {
        super(404, "Product not found: " + productId);
    }
}
