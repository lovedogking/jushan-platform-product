package com.jushan.common;

/**
 * 错误码接口。
 * <p>
 * 所有业务错误码枚举必须实现本接口，确保统一的结构和可序列化性。
 * <pre>{@code
 * public enum OrderErrorCode implements ErrorCode {
 *     ORDER_NOT_FOUND(20001, "订单不存在"),
 *     ORDER_STATUS_ILLEGAL(20002, "订单状态不允许当前操作");
 *
 *     private final int code;
 *     private final String message;
 *     // ...
 * }
 * }</pre>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface ErrorCode {

    /**
     * 错误码，0 表示成功。
     */
    int getCode();

    /**
     * 面向用户的错误描述，不含内部细节。
     */
    String getMessage();
}
