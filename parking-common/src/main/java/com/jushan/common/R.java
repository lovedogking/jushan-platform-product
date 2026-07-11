package com.jushan.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

/**
 * 统一响应结构。
 * <p>
 * 所有 Controller 返回本类型或其子类，确保前端解析一致性。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 成功返回数据
 * return R.ok(data);
 *
 * // 成功无数据
 * return R.ok();
 *
 * // 业务失败
 * return R.fail(CommonErrorCode.PARAM_ERROR);
 *
 * // 自定义状态码失败
 * return R.fail(20001, "订单不存在");
 * }</pre>
 *
 * @param <T> 响应数据类型
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码，0 表示成功 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 请求追踪 ID */
    private String traceId;

    /** 错误详情（仅校验失败等场景使用） */
    private Object errors;

    // ==================== 构造器 ====================

    private R() {}

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 静态工厂方法 ====================

    /** 成功（无数据） */
    public static <T> R<T> ok() {
        return new R<>(CommonErrorCode.SUCCESS.getCode(),
                CommonErrorCode.SUCCESS.getMessage(), null);
    }

    /** 成功（携带数据） */
    public static <T> R<T> ok(T data) {
        return new R<>(CommonErrorCode.SUCCESS.getCode(),
                CommonErrorCode.SUCCESS.getMessage(), data);
    }

    /** 成功（自定义消息 + 数据） */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(CommonErrorCode.SUCCESS.getCode(), message, data);
    }

    /** 失败（使用 ErrorCode） */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 失败（ErrorCode + 自定义消息） */
    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    /** 失败（自定义 code + message） */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    // ==================== Fluent 方法 ====================

    public R<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public R<T> errors(Object errors) {
        this.errors = errors;
        return this;
    }

    // ==================== Getter / Setter ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Object getErrors() {
        return errors;
    }

    public void setErrors(Object errors) {
        this.errors = errors;
    }

    // ==================== 便捷方法 ====================

    /** 是否成功 */
    public boolean isSuccess() {
        return this.code == CommonErrorCode.SUCCESS.getCode();
    }

    @Override
    public String toString() {
        return "R{code=" + code + ", message='" + message + "', data=" + data + "}";
    }
}
