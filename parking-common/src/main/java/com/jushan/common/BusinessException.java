package com.jushan.common;

/**
 * 业务异常。
 * <p>
 * 业务层遇到可预期但不应继续执行的情况时抛出，由全局异常处理器统一转换为
 * {@link R} 响应。携带 {@link ErrorCode} 以保持错误码和消息的结构化。
 *
 * <pre>{@code
 * throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
 * throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车牌号不能为空");
 * }</pre>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    /**
     * 使用预定义错误码构造。
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 使用预定义错误码 + 自定义消息构造（覆盖 errorCode 中的默认 message）。
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    /**
     * 使用预定义错误码 + 消息格式化参数构造。
     * <p>
     * 实际消息由调用方负责格式化；此处仅保存参数供日志或审计使用。
     */
    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 仅使用自定义 code 和 message（不绑定 ErrorCode 枚举）。
     * 应在无法预定义错误码的场景下使用，优先使用 {@link #BusinessException(ErrorCode)}。
     */
    public BusinessException(int code, String message) {
        super(message);
        this.errorCode = new ErrorCode() {
            @Override
            public int getCode() { return code; }
            @Override
            public String getMessage() { return message; }
        };
        this.args = null;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    @Override
    public String getMessage() {
        String msg = super.getMessage();
        return msg != null ? msg : errorCode.getMessage();
    }
}
