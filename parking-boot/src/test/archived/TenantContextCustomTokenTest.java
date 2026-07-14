package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 自定义 Token 请求头名称测试（P0 安全修复）。
 * <p>
 * 验证当 Sa-Token 配置使用非默认 Token 名称（如 {@code X-Custom-Auth}）时，
 * {@link com.jushan.framework.auth.TenantContextInterceptor} 仍能正确解析 Token
 * 并填充租户上下文。
 * <p>
 * 关键验证：上下文装配不硬编码 {@code Authorization} 请求头，
 * 而是依赖 Sa-Token 已完成的 Token 解析（通过 {@code StpUtil.isLogin()} 等 API）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "sa-token.token-name=X-Custom-Auth",
        "sa-token.token-prefix=Bearer"
})
@DisplayName("自定义 Token 请求头名称测试")
class TenantContextCustomTokenTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    /**
     * 使用自定义 Token 头 {@code X-Custom-Auth}。
     */
    private static final String CUSTOM_TOKEN_HEADER = "X-Custom-Auth";

    @BeforeEach
    void setUp() {
        SysUser admin = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, ADMIN_USERNAME));
        if (admin != null && !"ENABLED".equals(admin.getStatus())) {
            admin.setStatus("ENABLED");
            sysUserMapper.updateById(admin);
        }
    }

    @Test
    @DisplayName("自定义 Token 头登录成功 → 上下文正确填充")
    void shouldLoginAndPopulateContextWithCustomTokenHeader() throws Exception {
        // 1. 使用自定义 Token 头登录
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = extractToken(responseBody);

        // 2. 使用自定义 Token 头查询会话
        mockMvc.perform(get("/auth/session")
                        .header(CUSTOM_TOKEN_HEADER, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.displayName").value("超级管理员"))
                .andExpect(jsonPath("$.data.roles[0]").value("super_admin"));
    }

    @Test
    @DisplayName("使用标准 Authorization 头（非配置头）→ 被拒绝 401")
    void shouldRejectRequestWithStandardAuthorizationHeader() throws Exception {
        // 登录获取 Token
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = extractToken(responseBody);

        // 使用标准 Authorization 头（非配置的 X-Custom-Auth）→ Sa-Token 读不到 Token → 401
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("自定义 Token 头平台超级管理员 → 正确获得平台身份")
    void shouldIdentifyPlatformUserWithCustomHeader() throws Exception {
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = extractToken(responseBody);

        // 平台用户访问租户专属接口 → 403 "平台用户无租户范围"
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header(CUSTOM_TOKEN_HEADER, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("平台用户无租户范围")));
    }

    @Test
    @DisplayName("自定义 Token 头伪造 Token → 401")
    void shouldRejectFakeTokenWithCustomHeader() throws Exception {
        mockMvc.perform(get("/auth/session")
                        .header(CUSTOM_TOKEN_HEADER, "Bearer fake-token-custom"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
