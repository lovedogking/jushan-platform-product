package com.jushan.framework.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 TraceId 过滤器。
 * <p>
 * 每个 HTTP 请求分配唯一 traceId，写入：
 * <ul>
 *   <li>SLF4J MDC — 日志模式中通过 {@code %X{traceId}} 输出</li>
 *   <li>响应头 {@code X-Trace-Id} — 前端可获取用于问题定位</li>
 * </ul>
 * <p>
 * 请求结束后自动清理 MDC，避免线程池复用时的 context 泄漏。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** 响应头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC 键名，与 logback pattern 中的 %X{traceId} 对齐 */
    public static final String MDC_KEY = "traceId";

    /** 入站 TraceId 最大长度，防止超长字符串攻击 */
    private static final int MAX_TRACE_ID_LENGTH = 64;

    /** 入站 TraceId 合法字符：字母、数字、连字符、下划线 */
    private static final String TRACE_ID_PATTERN = "[a-zA-Z0-9_-]+";

    /**
     * 校验入站 TraceId 是否合法。
     * <p>
     * 合法条件：非空、长度 ≤64、仅包含字母数字连字符下划线。
     *
     * @param traceId 入站 TraceId
     * @return true 表示合法可复用
     */
    static boolean isValidTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return false;
        }
        return traceId.length() <= MAX_TRACE_ID_LENGTH
                && traceId.matches(TRACE_ID_PATTERN);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // 优先使用上游传入的 traceId（如网关或 Nginx 传入），否则生成新的
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!isValidTraceId(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        try {
            MDC.put(MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
