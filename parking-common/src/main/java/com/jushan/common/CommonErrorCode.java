package com.jushan.common;

/**
 * 通用错误码枚举。
 * <p>
 * 覆盖跨模块的通用场景。业务模块应定义自己的 {@link ErrorCode} 枚举，
 * 避免将业务特定错误码堆砌至此。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public enum CommonErrorCode implements ErrorCode {

    /** 操作成功 */
    SUCCESS(0, "操作成功"),

    /** 参数校验失败 */
    PARAM_ERROR(400, "参数错误"),

    /** 未认证（未登录或 token 过期） */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /** 无权限 */
    FORBIDDEN(403, "无权限访问"),

    /** 资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),

    /** 请求方法不支持 */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /** 业务异常（通用） */
    BUSINESS_ERROR(1000, "业务处理异常"),

    /** 系统内部错误 */
    INTERNAL_ERROR(9999, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
