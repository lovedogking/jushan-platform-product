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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WxUserController 集成测试。
 * <p>
 * 覆盖微信登录、车牌绑定、解绑和手机号绑定场景。
 * 使用 Mock 微信登录（wx.mock-login=true）。
 * <p>
 * 使用 Testcontainers MySQL（继承基类），Sa-Token 回退到内存存储（无 Redis）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
    "wx.mock-login=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
    "wx.mock-login=true"
})
@DisplayName("WxUserController 集成测试")
class WxUserControllerIntegrationTest extends TestcontainersBaseTest {

    /** Redis 7 容器（通过 @ServiceConnection 自动注入连接属性） */
    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    // ==================== ① Mock 微信登录 ====================

    @Test
    @DisplayName("Mock 登录成功 → 返回 Token 和用户信息")
    void shouldLoginWithMockCode() throws Exception {
        String mockCode = "test_mock_code_" + System.currentTimeMillis();
        
        mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\",\"nickname\":\"测试用户\",\"avatarUrl\":\"https://example.com/avatar.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.plateCount").value(0));
    }

    @Test
    @DisplayName("Mock 登录重复 code → 返回已有用户")
    void shouldReturnExistingUserForSameCode() throws Exception {
        String mockCode = "repeat_mock_code_" + System.currentTimeMillis();
        
        // 第一次登录
        mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\",\"nickname\":\"重复测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true));
        
        // 第二次登录同一 code
        mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));
    }

    // ==================== ② 登录后操作 ====================

    @Test
    @DisplayName("登录后可查询用户信息")
    void shouldGetUserInfoAfterLogin() throws Exception {
        String mockCode = "user_info_test_" + System.currentTimeMillis();
        
        // 登录
        String loginResp = mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\",\"nickname\":\"查询测试\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);
        
        // 查询用户信息
        mockMvc.perform(get("/wx/user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("查询**试"))
                .andExpect(jsonPath("$.data.plateCount").value(0));
    }

    // ==================== ③ 车牌绑定 ====================

    @Test
    @DisplayName("登录后可绑定车牌")
    void shouldBindPlateAfterLogin() throws Exception {
        String mockCode = "bind_plate_test_" + System.currentTimeMillis();
        String plate = "京A12345";
        
        // 登录
        String loginResp = mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);
        
        // 绑定车牌
        mockMvc.perform(post("/wx/plates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\",\"vehicleType\":\"SMALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.plate").value(plate.toUpperCase()))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.verifyStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("重复绑定同一车牌 → 冲突错误")
    void shouldRejectDuplicatePlateBinding() throws Exception {
        String mockCode = "duplicate_plate_test_" + System.currentTimeMillis();
        String plate = "京B66666";
        
        // 登录
        String loginResp = mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);
        
        // 第一次绑定
        mockMvc.perform(post("/wx/plates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\"}"))
                .andExpect(status().isOk());
        
        // 第二次绑定同一车牌
        mockMvc.perform(post("/wx/plates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("该车牌已绑定"));
    }

    // ==================== ④ 解绑车牌 ====================

    @Test
    @DisplayName("可解绑已绑定车牌")
    void shouldUnbindPlate() throws Exception {
        String mockCode = "unbind_plate_test_" + System.currentTimeMillis();
        String plate = "京C99999";
        
        // 登录
        String loginResp = mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);
        
        // 绑定车牌
        String bindResp = mockMvc.perform(post("/wx/plates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long bindingId = extractBindingId(bindResp);
        
        // 解绑车牌
        mockMvc.perform(delete("/wx/plates/" + bindingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== ⑤ 权限控制 ====================

    @Test
    @DisplayName("未登录访问受保护接口 → 401")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/wx/user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("伪造 Token → 401")
    void shouldRejectFakeToken() throws Exception {
        mockMvc.perform(get("/wx/user")
                        .header("Authorization", "Bearer fake-token-that-does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== ⑥ 参数校验 ====================

    @Test
    @DisplayName("空 code → 参数校验失败")
    void shouldRejectEmptyCode() throws Exception {
        mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("空车牌号 → 参数校验失败")
    void shouldRejectEmptyPlate() throws Exception {
        // 先登录
        String loginResp = mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"validation_test\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);
        
        // 绑定空车牌
        mockMvc.perform(post("/wx/plates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== ⑦ 绑定后用户信息 ====================

    @Test
    @DisplayName("绑定车牌后查询 → 返回车牌列表")
    void shouldReturnPlateListAfterBinding() throws Exception {
        String mockCode = "plate_list_test_" + System.currentTimeMillis();
        String plate = "京D11111";
        
        // 登录
        String loginResp = mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);
        
        // 绑定车牌
        mockMvc.perform(post("/wx/plates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\"}"))
                .andExpect(status().isOk());
        
        // 查询用户信息
        mockMvc.perform(get("/wx/user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.plateCount").value(1))
                .andExpect(jsonPath("$.data.plates[0].plate").value(plate.toUpperCase()))
                .andExpect(jsonPath("$.data.defaultPlate.plate").value(plate.toUpperCase()));
    }

    // ==================== 工具方法 ====================

    private String extractToken(String json) {
        // 从 {"code":0,...,"data":{"token":"xxx",...}} 中提取 token
        int start = json.indexOf("\"token\":\"") + 9;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private Long extractBindingId(String json) {
        // 从 {"code":0,...,"data":{"id":123,...}} 中提取 id
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        if (end < 0) {
            end = json.indexOf("}", start);
        }
        return Long.parseLong(json.substring(start, end).trim());
    }
}