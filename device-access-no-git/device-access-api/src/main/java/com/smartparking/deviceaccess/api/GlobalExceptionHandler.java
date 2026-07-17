package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.common.dto.Result;
import com.smartparking.deviceaccess.common.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>
 * 将模块内部异常统一转换为 {@link Result} 格式返回。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleDeviceNotFound(DeviceNotFoundException e) {
        log.warn("Device not found: {}", e.getMessage());
        return Result.fail(404, e.getMessage());
    }

    @ExceptionHandler(DeviceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDeviceAlreadyExists(DeviceAlreadyExistsException e) {
        log.warn("Device already exists: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MqttConnectionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleMqttConnection(MqttConnectionException e) {
        log.error("MQTT connection error: {}", e.getMessage());
        return Result.fail(503, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", msg);
        return Result.fail(400, msg);
    }

    @ExceptionHandler(DeviceAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleDeviceAccess(DeviceAccessException e) {
        log.error("Device access error: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ── v0.3 新增 ──

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleProductNotFound(ProductNotFoundException e) {
        log.warn("Product not found: {}", e.getMessage());
        return Result.fail(404, e.getMessage());
    }

    @ExceptionHandler(RelationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleRelationNotFound(RelationNotFoundException e) {
        log.warn("Relation not found: {}", e.getMessage());
        return Result.fail(404, e.getMessage());
    }

    @ExceptionHandler(RelationAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleRelationAlreadyExists(RelationAlreadyExistsException e) {
        log.warn("Relation already exists: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(InvalidRelationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleInvalidRelation(InvalidRelationException e) {
        log.warn("Invalid relation: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(CapabilityUnsupportedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result<Void> handleCapabilityUnsupported(CapabilityUnsupportedException e) {
        log.warn("Capability unsupported: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUnsupportedOperation(UnsupportedOperationException e) {
        log.warn("Unsupported operation: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        // 静默处理，不打印堆栈。通常是浏览器插件（如 Vite）请求了不存在的静态资源
        return Result.fail(404, "Not found");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception e) {
        log.error("Unexpected error", e);
        return Result.fail(500, "Internal server error");
    }
}
