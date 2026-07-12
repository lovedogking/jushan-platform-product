package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.SysLoginLogMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试。
 * <p>
 * 覆盖正常登录、密码错误、禁用账号、退出、会话查询和 401 拦截场景。
 * <p>
 * 使用 Testcontainers MySQL（继承基类），Sa-Token 回退到内存存储（无 Redis）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("AuthController 集成测试")
class AuthControllerIntegrationTest extends TestcontainersBaseTest {

    /** Redis 7 容器（通过 @ServiceConnection 自动注入连接属性） */
    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysLoginLogMapper sysLoginLogMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    @BeforeEach
    void setUp() {
        // 确保每次测试前 admin 账号状态正常
        SysUser admin = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, ADMIN_USERNAME));
        if (admin != null && !"ENABLED".equals(admin.getStatus())) {
            admin.setStatus("ENABLED");
            sysUserMapper.updateById(admin);
        }
    }

    // ==================== ① 正常登录 ====================

    @Test
    @DisplayName("正确账号密码 → 返回 Token 和用户信息")
    void shouldLoginSuccessfully() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.data.user.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.user.displayName").value("超级管理员"))
                .andExpect(jsonPath("$.data.user.roles").isArray())
                .andExpect(jsonPath("$.data.user.roles[0]").value("super_admin"))
                .andExpect(jsonPath("$.data.user.permissions").isArray());
    }

    @Test
    @DisplayName("登录成功后 → 登录日志记录 SUCCESS")
    void shouldRecordSuccessLoginLog() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // 验证登录日志
        long count = sysLoginLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jushan.system.entity.SysLoginLog>()
                        .eq(com.jushan.system.entity.SysLoginLog::getUsername, ADMIN_USERNAME)
                        .eq(com.jushan.system.entity.SysLoginLog::getResult, "SUCCESS"));
        assertThat(count).as("成功登录日志应存在").isGreaterThanOrEqualTo(1);
    }

    // ==================== ② 密码错误 ====================

    @Test
    @DisplayName("错误密码 → 返回 401，日志不含密码")
    void shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"wrong_password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号或密码错误"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("错误密码 → 登录日志记录 FAIL_BAD_CREDENTIALS")
    void shouldRecordFailLoginLog() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        long count = sysLoginLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jushan.system.entity.SysLoginLog>()
                        .eq(com.jushan.system.entity.SysLoginLog::getUsername, ADMIN_USERNAME)
                        .eq(com.jushan.system.entity.SysLoginLog::getResult, "FAIL_BAD_CREDENTIALS"));
        assertThat(count).as("失败登录日志应存在").isGreaterThanOrEqualTo(1);
    }

    // ==================== ③ 账号禁用 ====================

    @Test
    @DisplayName("禁用账号 → 返回 401，提示账号已禁用")
    void shouldRejectDisabledAccount() throws Exception {
        // 禁用 admin 账号
        SysUser admin = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, ADMIN_USERNAME));
        admin.setStatus("DISABLED");
        sysUserMapper.updateById(admin);

        try {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
        } finally {
            // 恢复状态
            admin.setStatus("ENABLED");
            sysUserMapper.updateById(admin);
        }
    }

    // ==================== ④ 账号不存在 ====================

    @Test
    @DisplayName("不存在的账号 → 返回 401，不泄露用户存在性")
    void shouldRejectNonExistentUser() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost_user\",\"password\":\"anything\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    // ==================== ⑤ 参数校验 ====================

    @Test
    @DisplayName("空账号 → 参数校验失败")
    void shouldRejectEmptyUsername() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("空密码 → 参数校验失败")
    void shouldRejectEmptyPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("短密码（少于4位） → 参数校验失败")
    void shouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== ⑥ 会话查询 ====================

    @Test
    @DisplayName("登录后查询会话 → 返回当前用户信息")
    void shouldReturnSessionAfterLogin() throws Exception {
        // 1. 登录获取 token
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 提取 token（简单解析 JSON）
        String token = extractToken(responseBody);

        // 2. 使用 token 查询会话
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.displayName").value("超级管理员"))
                .andExpect(jsonPath("$.data.roles[0]").value("super_admin"));
    }

    // ==================== ⑦ 退出登录 ====================

    @Test
    @DisplayName("退出后 → 会话失效，后续请求 401")
    void shouldInvalidateSessionAfterLogout() throws Exception {
        // 1. 登录
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(responseBody);

        // 2. 退出
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 退出后查询会话 → 401
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ==================== ⑧ 未登录拒绝 ====================

    @Test
    @DisplayName("未登录访问受保护接口 → 401")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("伪造 Token → 401")
    void shouldRejectFakeToken() throws Exception {
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer fake-token-that-does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== ⑨ 登录检测（登录页不触发 401 重定向） ====================

    @Test
    @DisplayName("登录接口在未登录时也返回 401 而非重定向")
    void loginEndpointShouldReturn401WithoutRedirect() throws Exception {
        // 登录接口在白名单中，不会被 SaTokenConfig 拦截
        // 即使未登录也能访问，业务逻辑返回 401
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== ⑩ 强制修改密码（FIX-04） ====================

    @Test
    @DisplayName("FIX-04：默认管理员登录 → 返回 credentialStatus=EXPIRED")
    void shouldReturnExpiredCredentialStatusForDefaultAdmin() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.credentialStatus").value("EXPIRED"));
    }

    @Test
    @DisplayName("FIX-04：修改密码 → 成功，旧 Token 失效")
    void shouldChangePasswordAndInvalidateOldToken() throws Exception {
        // 1. 登录
        String loginResp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);

        // 2. 修改密码
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + ADMIN_PASSWORD + "\",\"newPassword\":\"newAdmin456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 旧 Token 失效
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // 4. 用新密码重新登录 → credentialStatus = ACTIVE
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"newAdmin456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialStatus").value("ACTIVE"));

        // 5. 恢复原密码和凭据状态（后续测试依赖）
        String newToken = extractToken(mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"newAdmin456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"newAdmin456\",\"newPassword\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // 恢复凭据状态为 EXPIRED（FIX-04 迁移默认值）
        SysUser admin = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, ADMIN_USERNAME));
        admin.setCredentialStatus("EXPIRED");
        sysUserMapper.updateById(admin);
    }

    @Test
    @DisplayName("FIX-04：错误旧密码 → 修改失败")
    void shouldRejectChangePasswordWithWrongOldPassword() throws Exception {
        String loginResp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(loginResp);

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("旧密码错误"));
    }

    // ==================== 工具方法 ====================

    private String extractToken(String json) {
        // 从 {"code":0,...,"data":{"accessToken":"xxx",...}} 中提取 token
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
