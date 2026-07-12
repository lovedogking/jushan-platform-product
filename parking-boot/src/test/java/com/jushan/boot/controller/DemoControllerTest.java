package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.common.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 演示控制器测试 — 覆盖四类响应。
 * <p>
 * 验证全局异常处理是否正确将异常转换为统一 {@link R} 响应。
 * <p>
 * <strong>FIX-07：</strong>
 * 使用完整 Spring Boot 上下文（Testcontainers MySQL），与项目其他集成测试一致。
 * {@code /demo/**} 路径在 SaTokenConfig 中已排除鉴权，无需登录。
 */
@AutoConfigureMockMvc
@DisplayName("DemoController — 四类响应测试")
class DemoControllerTest extends TestcontainersBaseTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== ① 正常响应 ====================

    @Test
    @DisplayName("正常响应 → code=0, data 非空")
    void shouldReturnSuccess() throws Exception {
        mockMvc.perform(get("/demo/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("Hello, Jushan Platform!"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ==================== ② 参数校验失败 ====================

    @Test
    @DisplayName("参数校验失败 → code=400, errors 含字段详情")
    void shouldReturnParamError() throws Exception {
        mockMvc.perform(post("/demo/param-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数校验失败"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("参数校验失败 — 缺少请求体")
    void shouldReturnParamErrorWhenBodyMissing() throws Exception {
        mockMvc.perform(post("/demo/param-error")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ==================== ③ 业务异常 ====================

    @Test
    @DisplayName("业务异常 → code=1000, message 透传")
    void shouldReturnBusinessError() throws Exception {
        mockMvc.perform(get("/demo/business-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("演示业务异常"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ==================== ④ 未知异常 ====================

    @Test
    @DisplayName("未知异常 → code=9999, 不泄露堆栈, 返回 HTTP 500")
    void shouldReturnInternalError() throws Exception {
        mockMvc.perform(get("/demo/unknown-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(9999))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ==================== ⑤ 契约约束 ====================

    @Test
    @DisplayName("响应体中不存在 msg 字段（禁止兼容旧字段名）")
    void shouldNotContainMsgField() throws Exception {
        mockMvc.perform(get("/demo/ok"))
                .andExpect(jsonPath("$.msg").doesNotExist());
        mockMvc.perform(get("/demo/business-error"))
                .andExpect(jsonPath("$.msg").doesNotExist());
    }

    @Test
    @DisplayName("无数据响应 data 字段存在且为 null")
    void shouldContainDataFieldEvenWhenNull() throws Exception {
        mockMvc.perform(get("/demo/business-error"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"data\":null")));
    }

    // ==================== ⑥ TraceId 一致性 ====================

    @Test
    @DisplayName("同一次请求响应头和响应体 traceId 非空")
    void traceIdShouldBePresent() throws Exception {
        mockMvc.perform(get("/demo/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("请求结束后 MDC 被清理，不污染后续线程")
    void traceIdShouldBeClearedAfterRequest() throws Exception {
        mockMvc.perform(get("/demo/ok"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNull(MDC.get("traceId"),
                "MDC 应在请求结束后被清理");
    }
}
