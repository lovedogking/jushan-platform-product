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
import org.springframework.http.ResponseEntity;
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
import java.util.Map;
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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleServletParamException(Exception ex, HttpServletRequest request) {
        log.warn("[请求参数错误] uri={} type={} message={}",
                request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage());
        return R.<Void>fail(CommonErrorCode.PARAM_ERROR, "请求参数格式错误")
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    // ==================== 业务异常 ====================

    /**
     * 协议级错误码 → HTTP 状态码映射。
     * <p>
     * 只有携带明确协议语义的错误码才映射为对应 HTTP 状态；
     * 其余普通业务错误码保持 HTTP 200。
     */
    private static final Map<Integer, HttpStatus> PROTOCOL_STATUS_MAP = Map.of(
            400, HttpStatus.BAD_REQUEST,
            401, HttpStatus.UNAUTHORIZED,
            403, HttpStatus.FORBIDDEN,
            404, HttpStatus.NOT_FOUND,
            409, HttpStatus.CONFLICT,
            429, HttpStatus.TOO_MANY_REQUESTS
    );

    /**
     * 根据业务错误码解析 HTTP 状态。
     * 协议级错误码（400/401/403/404/409/429）映射为对应 HTTP 状态，
     * 其余返回 200。
     */
    static HttpStatus resolveHttpStatus(int code) {
        return PROTOCOL_STATUS_MAP.getOrDefault(code, HttpStatus.OK);
    }

    /**
     * 处理业务异常。
     * <p>
     * 错误码为 400/401/403/404/409/429 时返回对应 HTTP 状态码，
     * 其余普通业务错误码返回 HTTP 200 + code 非 0。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException ex,
                                                            HttpServletRequest request) {
        HttpStatus status = resolveHttpStatus(ex.getCode());
        log.warn("[业务异常] uri={} code={} httpStatus={} message={}",
                request.getRequestURI(), ex.getCode(), status.value(), ex.getMessage());
        R<Void> body = R.<Void>fail(ex.getErrorCode(), ex.getMessage())
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(body);
    }

    // ==================== Sa-Token 认证/授权异常 ====================

    /**
     * 处理未登录异常（Sa-Token NotLoginException）。
     * <p>
     * 返回 HTTP 401 + UNAUTHORIZED 错误码。
     */
    @ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleNotLoginException(cn.dev33.satoken.exception.NotLoginException ex,
                                            HttpServletRequest request) {
        log.warn("[未登录] uri={} message={}", request.getRequestURI(), ex.getMessage());
        return R.<Void>fail(CommonErrorCode.UNAUTHORIZED)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    /**
     * 处理无权限异常（Sa-Token NotPermissionException）。
     * <p>
     * 返回 HTTP 403 + FORBIDDEN 错误码。T13 细化权限后使用。
     */
    @ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleNotPermissionException(cn.dev33.satoken.exception.NotPermissionException ex,
                                                 HttpServletRequest request) {
        log.warn("[无权限] uri={} permission={}", request.getRequestURI(), ex.getPermission());
        return R.<Void>fail(CommonErrorCode.FORBIDDEN)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    /**
     * 处理无角色异常（Sa-Token NotRoleException）。
     * <p>
     * 返回 HTTP 403 + FORBIDDEN 错误码。
     */
    @ExceptionHandler(cn.dev33.satoken.exception.NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleNotRoleException(cn.dev33.satoken.exception.NotRoleException ex,
                                           HttpServletRequest request) {
        log.warn("[无角色] uri={} role={}", request.getRequestURI(), ex.getRole());
        return R.<Void>fail(CommonErrorCode.FORBIDDEN)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    // ==================== Spring 内置异常 ====================

    /**
     * 处理 404 — 资源不存在 / 接口不存在。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(Exception ex, HttpServletRequest request) {
        log.warn("[404] uri={}", request.getRequestURI());
        return R.<Void>fail(CommonErrorCode.NOT_FOUND)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY));
    }

    /**
     * 处理 405 — 请求方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
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
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
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
