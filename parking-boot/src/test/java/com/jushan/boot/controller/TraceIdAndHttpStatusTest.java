package com.jushan.boot.controller;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TraceId 校验 与 BusinessException HTTP 状态映射 测试。
 * <p>
 * 覆盖 R01-F 要求的 TraceId 合法性、连续请求不污染、HTTP 状态码映射。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("TraceId 校验 与 HTTP 状态映射")
class TraceIdAndHttpStatusTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== TraceId 合法入站 ====================

    @Nested
    @DisplayName("合法入站 TraceId")
    class LegalTraceId {

        @Test
        @DisplayName("合法 X-Trace-Id 被复用，Header 与 Body 一致")
        void shouldReuseLegalTraceId() throws Exception {
            String traceId = "client_trace_123";

            mockMvc.perform(get("/demo/ok")
                            .header("X-Trace-Id", traceId))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id", traceId))
                    .andExpect(jsonPath("$.traceId").value(traceId));
        }

        @Test
        @DisplayName("纯字母数字下划线连字符的 TraceId 被接受")
        void shouldAcceptAlphanumericTraceId() throws Exception {
            String traceId = "abc123_XYZ-99_test";

            mockMvc.perform(get("/demo/ok")
                            .header("X-Trace-Id", traceId))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id", traceId))
                    .andExpect(jsonPath("$.traceId").value(traceId));
        }
    }

    // ==================== TraceId 非法入站 ====================

    @Nested
    @DisplayName("非法入站 TraceId")
    class IllegalTraceId {

        @Test
        @DisplayName("超过 64 字符的 TraceId 被拒绝，服务端重新生成")
        void shouldRejectOverlongTraceId() throws Exception {
            String longTraceId = "a".repeat(65);

            mockMvc.perform(get("/demo/ok")
                            .header("X-Trace-Id", longTraceId))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id",
                            not(longTraceId)))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.traceId").value(
                            not(longTraceId)));
        }

        @Test
        @DisplayName("包含非法字符的 TraceId 被拒绝，服务端重新生成")
        void shouldRejectTraceIdWithIllegalChars() throws Exception {
            String badTraceId = "trace<script>alert(1)</script>";

            mockMvc.perform(get("/demo/ok")
                            .header("X-Trace-Id", badTraceId))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id",
                            not(badTraceId)))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.traceId").value(
                            not(badTraceId)));
        }

        @Test
        @DisplayName("空字符串 TraceId 被忽略，服务端生成新 TraceId")
        void shouldIgnoreEmptyTraceId() throws Exception {
            mockMvc.perform(get("/demo/ok")
                            .header("X-Trace-Id", ""))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Trace-Id",
                            not("")))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("纯空白 TraceId 被忽略，服务端生成新 TraceId")
        void shouldIgnoreBlankTraceId() throws Exception {
            mockMvc.perform(get("/demo/ok")
                            .header("X-Trace-Id", "   "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }
    }

    // ==================== 连续请求不污染 ====================

    @Nested
    @DisplayName("连续请求 MDC 不污染")
    class NoTraceIdLeak {

        @Test
        @DisplayName("连续两个无 TraceId 请求，各自生成不同 TraceId")
        void shouldGenerateDifferentTraceIds() throws Exception {
            var result1 = mockMvc.perform(get("/demo/ok"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andReturn();

            String traceId1 = com.jayway.jsonpath.JsonPath
                    .read(result1.getResponse().getContentAsString(), "$.traceId");

            var result2 = mockMvc.perform(get("/demo/ok"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andReturn();

            String traceId2 = com.jayway.jsonpath.JsonPath
                    .read(result2.getResponse().getContentAsString(), "$.traceId");

            org.junit.jupiter.api.Assertions.assertNotEquals(traceId1, traceId2,
                    "连续两次请求应生成不同的 traceId");
        }

        @Test
        @DisplayName("请求结束后当前线程 MDC 无残留")
        void shouldClearMdcAfterRequest() throws Exception {
            mockMvc.perform(get("/demo/ok"))
                    .andExpect(status().isOk());

            org.junit.jupiter.api.Assertions.assertNull(
                    MDC.get("traceId"),
                    "MDC 应在请求结束后被清理");
        }

        @Test
        @DisplayName("Header 与 Body 中的 TraceId 始终一致")
        void headerAndBodyTraceIdShouldMatch() throws Exception {
            var result = mockMvc.perform(get("/demo/ok"))
                    .andExpect(status().isOk())
                    .andReturn();

            String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
            String bodyTraceId = com.jayway.jsonpath.JsonPath
                    .read(result.getResponse().getContentAsString(), "$.traceId");

            org.junit.jupiter.api.Assertions.assertEquals(headerTraceId, bodyTraceId,
                    "Header 和 Body 中的 traceId 应一致");
        }
    }

    // ==================== HTTP 状态映射 ====================

    @Nested
    @DisplayName("BusinessException HTTP 状态码映射")
    class HttpStatusMapping {

        @Test
        @DisplayName("code=400 → HTTP 400")
        void code400ShouldMapToHttp400() throws Exception {
            mockMvc.perform(get("/test/error-400"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("参数错误"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("code=401 → HTTP 401")
        void code401ShouldMapToHttp401() throws Exception {
            mockMvc.perform(get("/test/error-401"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("未登录或登录已过期"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("code=403 → HTTP 403")
        void code403ShouldMapToHttp403() throws Exception {
            mockMvc.perform(get("/test/error-403"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("无权限访问"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("code=404 → HTTP 404")
        void code404ShouldMapToHttp404() throws Exception {
            mockMvc.perform(get("/test/error-404"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("请求的资源不存在"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("code=409 → HTTP 409")
        void code409ShouldMapToHttp409() throws Exception {
            mockMvc.perform(get("/test/error-409"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("code=429 → HTTP 429")
        void code429ShouldMapToHttp429() throws Exception {
            mockMvc.perform(get("/test/error-429"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(429))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("普通业务错误码（1000）→ HTTP 200")
        void normalBusinessCodeShouldMapToHttp200() throws Exception {
            mockMvc.perform(get("/demo/business-error"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        @Test
        @DisplayName("响应不泄露异常类名或堆栈")
        void shouldNotLeakInternalDetails() throws Exception {
            mockMvc.perform(get("/test/error-400"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            not(org.hamcrest.Matchers.containsString("Exception"))))
                    .andExpect(jsonPath("$.message").value(
                            not(org.hamcrest.Matchers.containsString("BusinessException"))))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }
    }

    // ==================== 测试专用 Controller ====================

    /**
     * 测试专用 Controller — 仅用于触发各类 BusinessException。
     * 不得在生产代码中复刻。
     */
    @RestController
    @RequestMapping("/test")
    static class TestErrorController {

        @GetMapping("/error-400")
        public R<Void> error400() {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "参数错误");
        }

        @GetMapping("/error-401")
        public R<Void> error401() {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }

        @GetMapping("/error-403")
        public R<Void> error403() {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权限访问");
        }

        @GetMapping("/error-404")
        public R<Void> error404() {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "请求的资源不存在");
        }

        @GetMapping("/error-409")
        public R<Void> error409() {
            throw new BusinessException(409, "资源冲突");
        }

        @GetMapping("/error-429")
        public R<Void> error429() {
            throw new BusinessException(429, "请求过于频繁");
        }
    }
}
