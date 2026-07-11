package com.jushan.framework.web;

import com.jushan.common.R;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应增强 — 自动注入 traceId。
 * <p>
 * 对所有 {@link R} 类型返回值，从当前线程 MDC 读取 traceId 并注入。
 * 确保正常响应和异常响应均携带 traceId，无需每个 Controller 手动设置。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestControllerAdvice
public class TraceIdResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 仅当返回类型为 R 或其子类时增强
        return R.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                   MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request,
                                   ServerHttpResponse response) {
        if (body instanceof R<?> r) {
            // 如果 traceId 已被显式设置（如 GlobalExceptionHandler），不覆盖
            if (r.getTraceId() == null || r.getTraceId().isBlank()) {
                String traceId = MDC.get(TraceIdFilter.MDC_KEY);
                if (traceId != null && !traceId.isBlank()) {
                    r.setTraceId(traceId);
                }
            }
        }
        return body;
    }
}
