package com.jushan.platform.infra.web;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（infrastructure 版本）。
 * <p>
 * 将各类异常统一转换为 {@link R} 响应：
 * <ul>
 *   <li>参数校验失败 → PARAM_ERROR (400)</li>
 *   <li>认证失败 → UNAUTHORIZED (401)</li>
 *   <li>权限不足 → FORBIDDEN (403)</li>
 *   <li>业务异常 → 对应错误码</li>
 *   <li>未知异常 → INTERNAL_ERROR (500)</li>
 * </ul>
 * <p>
 * <strong>P0 红线</strong>：禁止将堆栈 trace 或内部错误详情返回前端。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component("infraGlobalExceptionHandler")
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 参数校验失败 ====================

    /**
     * 处理 @Valid / @Validated 触发的 DTO 校验失败。
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
                .errors(errors);
    }

    /**
     * 处理缺少必填参数、类型不匹配、请求体不可读等异常。
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
        return R.<Void>fail(CommonErrorCode.PARAM_ERROR, "请求参数格式错误");
    }

    // ==================== 认证/授权异常 ====================

    /**
     * 处理 Spring Security 认证异常（未登录或 Token 无效）。
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleAuthenticationException(AuthenticationException ex,
                                                  HttpServletRequest request) {
        log.warn("[认证失败] uri={} message={}", request.getRequestURI(), ex.getMessage());
        return R.<Void>fail(CommonErrorCode.UNAUTHORIZED);
    }

    /**
     * 处理 Spring Security 授权异常（无权限）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDeniedException(AccessDeniedException ex,
                                                HttpServletRequest request) {
        log.warn("[无权限] uri={} message={}", request.getRequestURI(), ex.getMessage());
        return R.<Void>fail(CommonErrorCode.FORBIDDEN);
    }

    // ==================== 业务异常 ====================

    /**
     * 处理业务异常。
     * <p>
     * 根据错误码映射 HTTP 状态码：400/401/403/404/409/429 返回对应状态，其余返回 200。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException ex,
                                                            HttpServletRequest request) {
        HttpStatus status = resolveHttpStatus(ex.getCode());
        log.warn("[业务异常] uri={} code={} httpStatus={} message={}",
                request.getRequestURI(), ex.getCode(), status.value(), ex.getMessage());
        R<Void> body = R.<Void>fail(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 协议级错误码 → HTTP 状态码映射。
     */
    private static HttpStatus resolveHttpStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.OK;
        };
    }

    // ==================== Spring 内置异常 ====================

    /**
     * 处理 404 — 资源不存在。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("[404] uri={}", request.getRequestURI());
        return R.<Void>fail(CommonErrorCode.NOT_FOUND);
    }

    /**
     * 处理 405 — 请求方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                             HttpServletRequest request) {
        log.warn("[405] uri={} method={}", request.getRequestURI(), request.getMethod());
        return R.<Void>fail(CommonErrorCode.METHOD_NOT_ALLOWED);
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
        return R.<Void>fail(CommonErrorCode.INTERNAL_ERROR);
    }

    // ==================== 内部类型 ====================

    /**
     * 字段校验错误详情。
     */
    public record FieldErrorDetail(String field, String message) {}
}
