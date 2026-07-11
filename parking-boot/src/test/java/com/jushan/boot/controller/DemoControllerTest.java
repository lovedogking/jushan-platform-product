package com.jushan.boot.controller;

import com.jushan.common.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 演示控制器测试 — 覆盖四类响应。
 * <p>
 * 验证全局异常处理是否正确将异常转换为统一 {@link R} 响应。
 * <p>
 * 本测试不需要数据库，通过显式排除 DataSource 自动配置减少启动开销。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("DemoController — 四类响应测试")
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== ① 正常响应 ====================

    @Test
    @DisplayName("正常响应 → code=0, data 非空")
    void shouldReturnSuccess() throws Exception {
        mockMvc.perform(get("/demo/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value("Hello, Jushan Platform!"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ==================== ② 参数校验失败 ====================

    @Test
    @DisplayName("参数校验失败 → code=400, errors 含字段详情")
    void shouldReturnParamError() throws Exception {
        mockMvc.perform(post("/demo/param-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))   // 空名称触发 @NotBlank
                .andExpect(status().isOk())
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
                .andExpect(status().isOk())
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
    @DisplayName("未知异常 → code=9999, 不泄露堆栈")
    void shouldReturnInternalError() throws Exception {
        mockMvc.perform(get("/demo/unknown-error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(9999))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                // 确认不返回堆栈
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ==================== ⑤ TraceId 一致性 ====================

    @Test
    @DisplayName("同一次请求响应头和响应体 traceId 非空")
    void traceIdShouldBePresent() throws Exception {
        mockMvc.perform(get("/demo/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists("X-Trace-Id"));
    }
}
