package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 最小化调试测试：验证 Sa-Token 登录后 Token 能否用于后续请求。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("Token 调试测试")
class TokenDebugTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    private static final String SUPER_ADMIN = "admin";
    private static final String SUPER_ADMIN_PWD = "admin123";

    @Test
    @DisplayName("超级管理员登录并立即访问受保护端点")
    void debugLoginAndTokenFlow() throws Exception {
        // Step 1: Login
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + SUPER_ADMIN + "\",\"password\":\"" + SUPER_ADMIN_PWD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String loginBody = loginResult.getResponse().getContentAsString();
        System.out.println("=== LOGIN RESPONSE ===");
        System.out.println(loginBody);

        // Step 2: Extract token — try multiple strategies
        String token = extractTokenFlexible(loginBody);
        System.out.println("=== EXTRACTED TOKEN ===");
        System.out.println(token);

        // Step 3: Test without Bearer prefix
        System.out.println("=== TRYING without Bearer prefix ===");
        MvcResult result1 = mockMvc.perform(get("/admin/employees?page=1&size=10")
                        .header("Authorization", token))
                .andReturn();
        System.out.println("Status: " + result1.getResponse().getStatus());
        System.out.println("Body: " + result1.getResponse().getContentAsString());

        // Step 4: Test with Bearer prefix
        System.out.println("=== TRYING with Bearer prefix ===");
        MvcResult result2 = mockMvc.perform(get("/admin/employees?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        System.out.println("Status: " + result2.getResponse().getStatus());
        System.out.println("Body: " + result2.getResponse().getContentAsString());

        // Step 5: Test with "Bearer" (no space)
        System.out.println("=== TRYING with Bearer (no space) ===");
        MvcResult result3 = mockMvc.perform(get("/admin/employees?page=1&size=10")
                        .header("Authorization", "Bearer" + token))
                .andReturn();
        System.out.println("Status: " + result3.getResponse().getStatus());
        System.out.println("Body: " + result3.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("检查 /auth/login 是否被 SaInterceptor 排除")
    void debugLoginExclusion() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + SUPER_ADMIN + "\",\"password\":\"" + SUPER_ADMIN_PWD + "\"}"))
                .andReturn();
        System.out.println("=== LOGIN RESPONSE ===");
        System.out.println("Status: " + result.getResponse().getStatus());
        System.out.println("Body: " + result.getResponse().getContentAsString());
        // Should be 200 if login works
        Assertions.assertEquals(200, result.getResponse().getStatus());
    }

    private String extractTokenFlexible(String json) {
        // Strategy 1: "accessToken":"xxx"
        int idx = json.indexOf("\"accessToken\"");
        if (idx >= 0) {
            int start = json.indexOf("\"", idx + 14) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        // Strategy 2: "token":"xxx"
        idx = json.indexOf("\"token\"");
        if (idx >= 0) {
            int start = json.indexOf("\"", idx + 7) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        // Strategy 3: "tokenValue":"xxx"
        idx = json.indexOf("\"tokenValue\"");
        if (idx >= 0) {
            int start = json.indexOf("\"", idx + 12) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        return "TOKEN_NOT_FOUND";
    }
}
