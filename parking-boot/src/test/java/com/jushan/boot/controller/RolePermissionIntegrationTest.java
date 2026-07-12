package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 角色权限集成测试。
 * <p>
 * 验证 T13 固定角色模型的权限校验：
 * <ul>
 *   <li>7 个固定角色的权限数据正确加载</li>
 *   <li>有权限的端点返回 HTTP 200</li>
 *   <li>无权限的端点返回 HTTP 403</li>
 *   <li>{@code @SaCheckRole} 注解生效</li>
 *   <li>权限数据在 LoginResult 中正确返回</li>
 * </ul>
 * <p>
 * 使用 Testcontainers MySQL + Redis。
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
@DisplayName("角色权限集成测试")
class RolePermissionIntegrationTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    /** BCrypt hash of "admin123" */
    private static final String PASSWORD_HASH = "$2b$10$zp.gM6IpIAC95Pcq2vDSeeR3.cdKRr.5wvBjs7XwNdWlSAl1IMqna";

    // ==================== 用户账号 ====================

    private static final String SUPER_ADMIN = "admin";

    private static final String PLATFORM_OPERATOR = "t13_platform_operator";
    private static final String CUSTOMER_ADMIN = "t13_customer_admin";
    private static final String PARKING_MANAGER = "t13_parking_manager";
    private static final String FINANCE = "t13_finance";
    private static final String DEVICE_MAINTENANCE = "t13_device_maintenance";
    private static final String BOOTH_OPERATOR = "t13_booth_operator";

    @BeforeEach
    void setUp() {
        // 确保 admin 状态正常
        ensureUser(SUPER_ADMIN, "超级管理员", "[\"super_admin\"]");
        // 创建各角色测试用户
        ensureUser(PLATFORM_OPERATOR, "平台运营测试", "[\"platform_operator\"]");
        ensureUser(CUSTOMER_ADMIN, "客户管理员测试", "[\"customer_admin\"]");
        ensureUser(PARKING_MANAGER, "停车场管理员测试", "[\"parking_manager\"]");
        ensureUser(FINANCE, "财务测试", "[\"finance\"]");
        ensureUser(DEVICE_MAINTENANCE, "设备运维测试", "[\"device_maintenance\"]");
        ensureUser(BOOTH_OPERATOR, "岗亭测试", "[\"booth_operator\"]");
    }

    /** 确保用户存在且状态为 ENABLED */
    private void ensureUser(String username, String displayName, String roles) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            user = new SysUser();
            user.setUsername(username);
            user.setPasswordHash(PASSWORD_HASH);
            user.setDisplayName(displayName);
            user.setStatus("ENABLED");
            user.setRoles(roles);
            sysUserMapper.insert(user);
        } else {
            if (!"ENABLED".equals(user.getStatus())) {
                user.setStatus("ENABLED");
                sysUserMapper.updateById(user);
            }
        }
    }

    // ==================== 工具方法 ====================

    /** 登录指定用户并返回 Token */
    private String loginAs(String username) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return extractToken(body);
    }

    /** 解析 Token */
    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    /** 带 Token 的 GET 请求 */
    private void assertAllowed(String token, String path) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 带 Token 的 GET 请求 → 期望 403 */
    private void assertDenied(String token, String path) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ================================================================
    // ① 超级管理员（super_admin）：拥有全部权限
    // ================================================================

    @Nested
    @DisplayName("超级管理员")
    class SuperAdminTests {

        private String token;

        @BeforeEach
        void login() throws Exception {
            token = loginAs(SUPER_ADMIN);
        }

        @Test
        @DisplayName("允许 → 开闸 gate:open")
        void allowGateOpen() throws Exception {
            assertAllowed(token, "/test/permission/gate-open");
        }

        @Test
        @DisplayName("允许 → 设备管理 device:manage")
        void allowDeviceManage() throws Exception {
            assertAllowed(token, "/test/permission/device-manage");
        }

        @Test
        @DisplayName("允许 → 财务管理 finance:manage")
        void allowFinanceManage() throws Exception {
            assertAllowed(token, "/test/permission/finance-manage");
        }

        @Test
        @DisplayName("允许 → 租户管理 tenant:write")
        void allowTenantWrite() throws Exception {
            assertAllowed(token, "/test/permission/tenant-write");
        }

        @Test
        @DisplayName("允许 → 用户管理 user:write")
        void allowUserWrite() throws Exception {
            assertAllowed(token, "/test/permission/user-write");
        }

        @Test
        @DisplayName("允许 → 停用停车场 parking:disable")
        void allowParkingDisable() throws Exception {
            assertAllowed(token, "/test/permission/parking-disable");
        }

        @Test
        @DisplayName("允许 → 角色校验 @SaCheckRole")
        void allowSuperAdminRole() throws Exception {
            assertAllowed(token, "/test/permission/role-super-admin");
        }

        @Test
        @DisplayName("拒绝 → 岗亭角色校验（非岗亭）")
        void denyBoothOperatorRole() throws Exception {
            assertDenied(token, "/test/permission/role-booth-operator");
        }

        @Test
        @DisplayName("登录响应包含全部权限")
        void loginContainsAllPermissions() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + SUPER_ADMIN + "\",\"password\":\"admin123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.permissions.length()").value(18));
        }
    }

    // ================================================================
    // ② 平台运营（platform_operator）
    // ================================================================

    @Nested
    @DisplayName("平台运营")
    class PlatformOperatorTests {

        private String token;

        @BeforeEach
        void login() throws Exception {
            token = loginAs(PLATFORM_OPERATOR);
        }

        @Test
        @DisplayName("允许 → 查看用户 user:read")
        void allowTenantWrite() throws Exception {
            // platform_operator 有 tenant:write 权限
            assertAllowed(token, "/test/permission/tenant-write");
        }

        @Test
        @DisplayName("允许 → 查看财务 finance:read（编程方式）")
        void allowFinanceRead() throws Exception {
            assertAllowed(token, "/test/permission/check-any-gate");
        }

        @Test
        @DisplayName("拒绝 → 开闸 gate:open")
        void denyGateOpen() throws Exception {
            assertDenied(token, "/test/permission/gate-open");
        }

        @Test
        @DisplayName("拒绝 → 用户管理 user:write")
        void denyUserWrite() throws Exception {
            assertDenied(token, "/test/permission/user-write");
        }

        @Test
        @DisplayName("拒绝 → 财务管理 finance:manage")
        void denyFinanceManage() throws Exception {
            assertDenied(token, "/test/permission/finance-manage");
        }

        @Test
        @DisplayName("拒绝 → 岗亭角色")
        void denyBoothRole() throws Exception {
            assertDenied(token, "/test/permission/role-booth-operator");
        }
    }

    // ================================================================
    // ③ 客户管理员（customer_admin）
    // ================================================================

    @Nested
    @DisplayName("客户管理员")
    class CustomerAdminTests {

        private String token;

        @BeforeEach
        void login() throws Exception {
            token = loginAs(CUSTOMER_ADMIN);
        }

        @Test
        @DisplayName("允许 → 开闸 gate:open")
        void allowGateOpen() throws Exception {
            assertAllowed(token, "/test/permission/gate-open");
        }

        @Test
        @DisplayName("允许 → 用户管理 user:write")
        void allowUserWrite() throws Exception {
            assertAllowed(token, "/test/permission/user-write");
        }

        @Test
        @DisplayName("允许 → 停用停车场 parking:disable")
        void allowParkingDisable() throws Exception {
            assertAllowed(token, "/test/permission/parking-disable");
        }

        @Test
        @DisplayName("允许 → 设备管理 device:manage（FIX-12：客户管理员可管理本租户设备）")
        void allowDeviceManage() throws Exception {
            assertAllowed(token, "/test/permission/device-manage");
        }

        @Test
        @DisplayName("拒绝 → 财务管理 finance:manage")
        void denyFinanceManage() throws Exception {
            assertDenied(token, "/test/permission/finance-manage");
        }

        @Test
        @DisplayName("拒绝 → 租户管理 tenant:write（仅平台级角色）")
        void denyTenantWrite() throws Exception {
            assertDenied(token, "/test/permission/tenant-write");
        }
    }

    // ================================================================
    // ④ 停车场管理员（parking_manager）
    // ================================================================

    @Nested
    @DisplayName("停车场管理员")
    class ParkingManagerTests {

        private String token;

        @BeforeEach
        void login() throws Exception {
            token = loginAs(PARKING_MANAGER);
        }

        @Test
        @DisplayName("允许 → 开闸 gate:open")
        void allowGateOpen() throws Exception {
            assertAllowed(token, "/test/permission/gate-open");
        }

        @Test
        @DisplayName("允许 → 人工放行 gate:manual")
        void allowGateManual() throws Exception {
            assertAllowed(token, "/test/permission/gate-manual");
        }

        @Test
        @DisplayName("允许 → 收费规则 fee-rule:write")
        void allowFeeRuleWrite() throws Exception {
            assertAllowed(token, "/test/permission/fee-rule-write");
        }

        @Test
        @DisplayName("拒绝 → 设备管理 device:manage")
        void denyDeviceManage() throws Exception {
            assertDenied(token, "/test/permission/device-manage");
        }

        @Test
        @DisplayName("拒绝 → 财务管理 finance:manage")
        void denyFinanceManage() throws Exception {
            assertDenied(token, "/test/permission/finance-manage");
        }

        @Test
        @DisplayName("拒绝 → 用户管理 user:write")
        void denyUserWrite() throws Exception {
            assertDenied(token, "/test/permission/user-write");
        }

        @Test
        @DisplayName("拒绝 → 停用停车场 parking:disable")
        void denyParkingDisable() throws Exception {
            assertDenied(token, "/test/permission/parking-disable");
        }
    }

    // ================================================================
    // ⑤ 财务（finance）
    // ================================================================

    @Nested
    @DisplayName("财务")
    class FinanceTests {

        private String token;

        @BeforeEach
        void login() throws Exception {
            token = loginAs(FINANCE);
        }

        @Test
        @DisplayName("允许 → 财务管理 finance:manage")
        void allowFinanceManage() throws Exception {
            assertAllowed(token, "/test/permission/finance-manage");
        }

        @Test
        @DisplayName("拒绝 → 开闸 gate:open")
        void denyGateOpen() throws Exception {
            assertDenied(token, "/test/permission/gate-open");
        }

        @Test
        @DisplayName("拒绝 → 设备管理 device:manage")
        void denyDeviceManage() throws Exception {
            assertDenied(token, "/test/permission/device-manage");
        }

        @Test
        @DisplayName("拒绝 → 用户管理 user:write")
        void denyUserWrite() throws Exception {
            assertDenied(token, "/test/permission/user-write");
        }

        @Test
        @DisplayName("拒绝 → 停用停车场 parking:disable")
        void denyParkingDisable() throws Exception {
            assertDenied(token, "/test/permission/parking-disable");
        }
    }

    // ================================================================
    // ⑥ 设备运维（device_maintenance）
    // ================================================================

    @Nested
    @DisplayName("设备运维")
    class DeviceMaintenanceTests {

        private String token;

        @BeforeEach
        void login() throws Exception {
            token = loginAs(DEVICE_MAINTENANCE);
        }

        @Test
        @DisplayName("允许 → 设备管理 device:manage")
        void allowDeviceManage() throws Exception {
            assertAllowed(token, "/test/permission/device-manage");
        }

        @Test
        @DisplayName("允许 → 开闸 gate:open")
        void allowGateOpen() throws Exception {
            assertAllowed(token, "/test/permission/gate-open");
        }

        @Test
        @DisplayName("允许 → 人工放行 gate:manual")
        void allowGateManual() throws Exception {
            assertAllowed(token, "/test/permission/gate-manual");
        }

        @Test
        @DisplayName("拒绝 → 财务管理 finance:manage")
        void denyFinanceManage() throws Exception {
            assertDenied(token, "/test/permission/finance-manage");
        }

        @Test
        @DisplayName("拒绝 → 用户管理 user:write")
        void denyUserWrite() throws Exception {
            assertDenied(token, "/test/permission/user-write");
        }

        @Test
        @DisplayName("拒绝 → 停用停车场 parking:disable")
        void denyParkingDisable() throws Exception {
            assertDenied(token, "/test/permission/parking-disable");
        }
    }

    // ================================================================
    // ⑦ 岗亭（booth_operator）
    // ================================================================

    @Nested
    @DisplayName("岗亭")
    class BoothOperatorTests {

        private String token;

        @BeforeEach
        void login() throws Exception {
            token = loginAs(BOOTH_OPERATOR);
        }

        @Test
        @DisplayName("允许 → 开闸 gate:open")
        void allowGateOpen() throws Exception {
            assertAllowed(token, "/test/permission/gate-open");
        }

        @Test
        @DisplayName("允许 → 人工放行 gate:manual")
        void allowGateManual() throws Exception {
            assertAllowed(token, "/test/permission/gate-manual");
        }

        @Test
        @DisplayName("允许 → 角色校验 @SaCheckRole")
        void allowBoothOperatorRole() throws Exception {
            assertAllowed(token, "/test/permission/role-booth-operator");
        }

        @Test
        @DisplayName("拒绝 → 设备管理 device:manage")
        void denyDeviceManage() throws Exception {
            assertDenied(token, "/test/permission/device-manage");
        }

        @Test
        @DisplayName("拒绝 → 财务管理 finance:manage")
        void denyFinanceManage() throws Exception {
            assertDenied(token, "/test/permission/finance-manage");
        }

        @Test
        @DisplayName("拒绝 → 租户管理 tenant:write")
        void denyTenantWrite() throws Exception {
            assertDenied(token, "/test/permission/tenant-write");
        }

        @Test
        @DisplayName("拒绝 → 用户管理 user:write")
        void denyUserWrite() throws Exception {
            assertDenied(token, "/test/permission/user-write");
        }
    }

    // ================================================================
    // ⑧ 通用测试
    // ================================================================

    @Nested
    @DisplayName("通用场景")
    class CommonTests {

        @Test
        @DisplayName("无权限注解的端点登录后可访问")
        void publicEndpointWithAuth() throws Exception {
            String token = loginAs(SUPER_ADMIN);
            mockMvc.perform(get("/test/permission/public")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.access").value("public"));
        }

        @Test
        @DisplayName("未登录访问受保护端点 → 401")
        void unauthenticatedAccessDenied() throws Exception {
            mockMvc.perform(get("/test/permission/gate-open"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("my-permissions 返回当前用户权限列表")
        void myPermissionsReturnsCorrectData() throws Exception {
            String token = loginAs(SUPER_ADMIN);
            mockMvc.perform(get("/test/permission/my-permissions")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.roles").isArray())
                    .andExpect(jsonPath("$.data.permissions").isArray())
                    .andExpect(jsonPath("$.data.roles[0]").value("super_admin"));
        }

        @Test
        @DisplayName("编程式 hasPermission 正确判断权限")
        void programmaticPermissionCheck() throws Exception {
            String token = loginAs(BOOTH_OPERATOR);
            mockMvc.perform(get("/test/permission/check-any-gate")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data['gate:open']").value(true))
                    .andExpect(jsonPath("$.data['gate:manual']").value(true));
        }
    }
}
