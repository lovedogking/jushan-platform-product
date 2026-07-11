package com.jushan.framework.web;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 将四类异常统一转换为 {@link R} 响应：
 * <ul>
 *   <li>参数校验失败 — MethodArgumentNotValidException / ConstraintViolationException → PARAM_ERROR</li>
 *   <li>业务异常 — BusinessException → errorCode 透传</li>
 *   <li>未知异常 — Exception → INTERNAL_ERROR（不泄露堆栈）</li>
 *   <li>其他 Spring 内置异常 — 404/405 → NOT_FOUND / METHOD_NOT_ALLOWED</li>
 * </ul>
 * <p>
 * <strong>P0 红线</strong>：禁止将堆栈 trace 或内部错误详情返回前端。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 参数校验失败 ====================

    /**
     * 处理 @Valid / @Validated 触发的 DTO 校验失败。
     * 提取 field → message 列表作为 errors 详情。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                 HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("[参数校验失败] uri={} errors={}", request.getRequestURI(), errors);
        return R.<Void>fail(CommonErrorCode.PARAM_ERROR, "参数校验失败")
                .traceId(MDC.get(TraceIdFilter.MDC_KEY))
                .errors(errors);
    }

    /**
     * 处理 @Validated 在 Controller 类级别触发的校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleConstraintViolation(ConstraintViolationException ex,
                                              HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations()
                .stream()
                .map(v -> new FieldErrorDetail(
                        v.getPropertyPath().toString(),
                        v.getMessage()))
                .collect(Collectors.toList());

        log.warn("[参数校验失败] uri={} errors={}", request.getRequestURI(), errors);
        return R.<Void>fail(CommonErrorCode.PARAM_ERROR, "参数校验失败")
                .traceId(MDC.get(TraceIdFilter.MDC_KEY))
                .errors(errors);
    }

    /**
     * 处理缺少必填参数、类型不匹配、请求体不可读等 Servlet 层参数异常。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleServletParamException(Exception ex, HttpServletRequest request) {
        log.warn("[请求参数错误] uri={} type={} message={}",
                request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage());
        return R.<Void>fail(CommonErrorCode.PARAM_ERROR, "请求参数格式错误")
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    // ==================== 业务异常 ====================

    /**
     * 处理业务异常，直接透传 ErrorCode。
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("[业务异常] uri={} code={} message={}",
                request.getRequestURI(), ex.getCode(), ex.getMessage());
        return R.<Void>fail(ex.getErrorCode(), ex.getMessage())
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    // ==================== Spring 内置异常 ====================

    /**
     * 处理 404 — 资源不存在 / 接口不存在。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleNotFound(Exception ex, HttpServletRequest request) {
        log.warn("[404] uri={}", request.getRequestURI());
        return R.<Void>fail(CommonErrorCode.NOT_FOUND)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    /**
     * 处理 405 — 请求方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                             HttpServletRequest request) {
        log.warn("[405] uri={} method={}", request.getRequestURI(), request.getMethod());
        return R.<Void>fail(CommonErrorCode.METHOD_NOT_ALLOWED)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    // ==================== 兜底：未知异常 ====================

    /**
     * 处理所有未预期的异常。
     * 返回统一错误信息，内部日志记录完整堆栈，<strong>不向前端泄露</strong>。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("[系统异常] uri={} type={} message={}",
                request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);
        return R.<Void>fail(CommonErrorCode.INTERNAL_ERROR)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    // ==================== 内部类型 ====================

    /**
     * 字段校验错误详情（用于 errors 数组）。
     */
    public record FieldErrorDetail(String field, String message) {}
}
