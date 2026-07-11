package com.jushan.framework.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TraceIdFilter 校验方法直接单元测试。
 * <p>
 * 覆盖 MockMvc 因底层容器限制无法直接构造的非法 TraceId 场景。
 */
@DisplayName("TraceIdFilter 入站校验")
class TraceIdFilterTest {

    @Test
    @DisplayName("合法 TraceId 通过校验")
    void shouldAcceptValidTraceId() {
        assertTrue(TraceIdFilter.isValidTraceId("client_trace_123"));
        assertTrue(TraceIdFilter.isValidTraceId("abc123_XYZ-99_test"));
        assertTrue(TraceIdFilter.isValidTraceId("a"));            // 最短合法
        assertTrue(TraceIdFilter.isValidTraceId("a".repeat(64))); // 最长合法
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n", "\r", "\r\n"})
    @DisplayName("null、空、纯空白 TraceId 不合法")
    void shouldRejectNullOrBlank(String input) {
        assertFalse(TraceIdFilter.isValidTraceId(input));
    }

    @Test
    @DisplayName("超过 64 字符不合法")
    void shouldRejectOverlong() {
        assertFalse(TraceIdFilter.isValidTraceId("a".repeat(65)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "abc\n123",           // 含换行
        "abc\r123",           // 含回车
        "trace<script>x",     // 含尖括号
        "a b c",              // 含空格
        "中文trace",           // 含中文
        "trace;drop table",   // 含分号
        "../etc/passwd"       // 含路径分隔符
    })
    @DisplayName("含非法字符的 TraceId 不合法")
    void shouldRejectIllegalCharacters(String input) {
        assertFalse(TraceIdFilter.isValidTraceId(input));
    }
}
