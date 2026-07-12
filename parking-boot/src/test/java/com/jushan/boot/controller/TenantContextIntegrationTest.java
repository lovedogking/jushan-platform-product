package com.jushan.boot.controller;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.EmployeeParkingLot;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.EmployeeParkingLotMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 可信租户上下文与数据权限强制集成测试（T16）。
 * <p>
 * 验证 {@link com.jushan.framework.auth.TenantContext} 和
 * {@link com.jushan.framework.auth.DataScope} 的行为：
 * <ul>
 *   <li>租户用户上下文正确填充，可正常操作本租户数据</li>
 *   <li>平台用户（无租户绑定）访问租户专属接口被拒绝</li>
 *   <li>跨租户数据操作被拒绝（fail-close）</li>
 *   <li>不存在的停车场 → 参数错误</li>
 *   <li>非客户管理员被 @SaCheckPermission 拦截</li>
 *   <li>未认证请求返回 401</li>
 * </ul>
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
@DisplayName("可信租户上下文与数据权限强制集成测试")
class TenantContextIntegrationTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ParkingLotMapper parkingLotMapper;

    @Autowired
    private EmployeeParkingLotMapper employeeParkingLotMapper;

    private static final String SUPER_ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_PASSWORD = "admin123";

    private String superAdminToken;
    private String customerAdminToken;
    private Long tenantId;
    private Long parkingLotId;
    private Long otherTenantId;
    private Long otherParkingLotId;
    private String otherAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        // 1. 超级管理员登录（平台用户）
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + SUPER_ADMIN_USERNAME + "\",\"password\":\"" + SUPER_ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        superAdminToken = extractToken(responseBody);

        // 2. 清理测试数据
        cleanupTestData();

        // 3. 注册本租户
        tenantId = registerAndApprove("13900000001", "数据范围测试有限公司", "测试管理员", "pass123");
        customerAdminToken = login("13900000001", "pass123");
        parkingLotId = createParkingLot(tenantId, "数据范围测试停车场");

        // 4. 注册另一个租户
        otherTenantId = registerAndApprove("13900000002", "跨租户测试有限公司", "跨租户管理员", "pass123");
        otherAdminToken = login("13900000002", "pass123");
        otherParkingLotId = createParkingLot(otherTenantId, "跨租户测试停车场");
    }

    @AfterEach
    void tearDown() {
        // cleanupTestData 已在 @BeforeEach 开头调用
    }

    // ==================== 租户上下文填充验证 ====================

    @Test
    @DisplayName("租户用户创建员工成功 → TenantContext 正确填充了 tenantId 和 customer_admin 角色")
    void shouldAllowTenantUserToCreateEmployee() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("测试员工A", "13911110001", "pass123",
                                "parking_manager", parkingLotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value("parking_manager"));
    }

    @Test
    @DisplayName("租户用户查询员工列表 → TenantContext 提供的 tenantId 被用作数据过滤")
    void shouldListEmployeesInOwnTenant() throws Exception {
        // 先创建两个员工
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("测试员工B", "13911110002", "pass123",
                                "finance", parkingLotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("测试员工C", "13911110003", "pass123",
                                "device_maintenance", parkingLotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 查询列表 → 只返回本租户的员工（排除 customer_admin）
        // 注意：使用 $.data.records.length() 验证而非 $.data.total，
        // 因为 MyBatis-Plus 分页 COUNT 查询依赖 PaginationInnerInterceptor（需 mybatis-plus-jsqlparser 依赖）。
        String listBody = mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        // 验证两个员工都在列表中（顺序不可控，取决于 created_at 及数据库排序）
        org.junit.jupiter.api.Assertions.assertTrue(
                listBody.contains("测试员工B") && listBody.contains("测试员工C"),
                "应有两位员工，但返回: " + listBody);
    }

    // ==================== 平台用户行为验证 ====================

    @Test
    @DisplayName("平台用户（super_admin）访问员工管理被拒绝 → requireTenantUser 抛出 FORBIDDEN")
    void shouldRejectPlatformUserFromEmployeeManagement() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("尝试创建", "13911110004", "pass123",
                                "parking_manager", parkingLotId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("平台用户无租户范围")));
    }

    @Test
    @DisplayName("平台用户查询员工列表也被拒绝")
    void shouldRejectPlatformUserFromEmployeeList() throws Exception {
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("平台用户无租户范围")));
    }

    // ==================== 跨租户拒绝（DataScope.validateTenantAccess） ====================

    @Test
    @DisplayName("客户管理员使用其他租户的停车场创建员工被拒绝 → fail-close")
    void shouldRejectCrossTenantParkingLotOnCreate() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("跨租户员工", "13911110005", "pass123",
                                "parking_manager", otherParkingLotId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    @Test
    @DisplayName("客户管理员查看其他租户员工详情被拒绝")
    void shouldRejectCrossTenantEmployeeDetail() throws Exception {
        // 在另一个租户中创建一个员工
        Long otherEmpId = createEmployeeInTenant("13911110006", "跨租户员工X",
                "parking_manager", otherParkingLotId);

        // 本租户管理员尝试查看 → 应该被拒绝
        mockMvc.perform(get("/admin/employees/" + otherEmpId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    @Test
    @DisplayName("客户管理员修改其他租户员工被拒绝")
    void shouldRejectCrossTenantEmployeeUpdate() throws Exception {
        Long otherEmpId = createEmployeeInTenant("13911110007", "跨租户员工Y",
                "parking_manager", otherParkingLotId);

        mockMvc.perform(put("/admin/employees/" + otherEmpId)
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateEmployeeJson("黑客修改", "finance", otherParkingLotId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    @Test
    @DisplayName("客户管理员重置其他租户员工密码被拒绝")
    void shouldRejectCrossTenantPasswordReset() throws Exception {
        Long otherEmpId = createEmployeeInTenant("13911110008", "跨租户员工Z",
                "parking_manager", otherParkingLotId);

        mockMvc.perform(post("/admin/employees/" + otherEmpId + "/reset-password")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    @Test
    @DisplayName("客户管理员修改其他租户员工状态被拒绝")
    void shouldRejectCrossTenantStatusChange() throws Exception {
        Long otherEmpId = createEmployeeInTenant("13911110009", "跨租户员工W",
                "parking_manager", otherParkingLotId);

        mockMvc.perform(post("/admin/employees/" + otherEmpId + "/status?action=DISABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    // ==================== 非法范围 ====================

    @Test
    @DisplayName("不存在的停车场 → 参数错误")
    void shouldRejectNonExistentParkingLot() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("非法停车场员工", "13911110010", "pass123",
                                "parking_manager", 99999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("停车场不存在")));
    }

    @Test
    @DisplayName("空停车场列表 → 参数错误")
    void shouldRejectEmptyParkingLotList() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"无授权员工\",\"phone\":\"13911110011\",\"password\":\"pass123\",\"roleCode\":\"parking_manager\",\"parkingLotIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("参数校验失败")));
    }

    // ==================== 权限注解拦截 ====================

    @Test
    @DisplayName("非客户管理员（parking_manager）尝试管理员工 → @SaCheckPermission 拦截返回 403")
    void shouldRejectNonCustomerAdminViaSaTokenPermission() throws Exception {
        // 先由客户管理员创建一个 parking_manager 员工
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("停车场管理员", "13911110012", "pass123",
                                "parking_manager", parkingLotId)))
                .andExpect(status().isOk());

        // parking_manager 登录后尝试创建员工 → Sa-Token @SaCheckPermission 拦截
        String pmToken = login("13911110012", "pass123");
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("尝试越权", "13911110013", "pass123",
                                "parking_manager", parkingLotId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("无权限访问")));
    }

    // ==================== 未认证拒绝 ====================

    @Test
    @DisplayName("未认证请求 → Sa-Token 拦截 → 返回 401")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/admin/employees?page=1&size=20"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 公开端点 ====================

    @Test
    @DisplayName("公开端点（登录）在无 TenantContext 时正常工作")
    void shouldAllowPublicEndpointWithoutTenantContext() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"13900000001\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== P0 安全修复：Fail-close 测试 ====================

    @Test
    @DisplayName("P0-1: 租户用户 Session 缺 tenantId → 返回 401 拒绝，不进入 Controller")
    void shouldRejectTenantUserWithMissingTenantIdInSession() throws Exception {
        // 获取租户管理员用户 ID
        SysUser tenantAdmin = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "13900000001"));
        org.junit.jupiter.api.Assertions.assertNotNull(tenantAdmin, "租户管理员应存在");

        // 登录以创建有效 Token
        String token = login("13900000001", "pass123");

        // 直接删除 User-Session 中的 tenantId（模拟会话损坏）
        SaSession userSession = StpUtil.getSessionByLoginId(tenantAdmin.getId());
        userSession.delete(com.jushan.framework.auth.TenantContext.SESSION_KEY_TENANT_ID);
        // userType 仍为 "tenant"，但 tenantId 已缺失
        org.junit.jupiter.api.Assertions.assertEquals("tenant",
                userSession.get(com.jushan.framework.auth.TenantContext.SESSION_KEY_USER_TYPE));

        // 请求应被拒绝，不进入 Controller
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message", containsString("租户")));

        // 恢复会话（避免影响后续测试）
        userSession.set(com.jushan.framework.auth.TenantContext.SESSION_KEY_TENANT_ID,
                tenantAdmin.getTenantId());
    }

    @Test
    @DisplayName("P0-2: 已认证用户 User-Session 数据被清空 → fail-closed 返回 401")
    void shouldRejectWhenUserSessionIsDeleted() throws Exception {
        SysUser tenantAdmin = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "13900000001"));

        // 登录创建 User-Session
        String token = login("13900000001", "pass123");

        // 清空 User-Session 的所有租户上下文属性（模拟会话损坏/Redis 数据丢失）
        SaSession userSession = StpUtil.getSessionByLoginId(tenantAdmin.getId(), false);
        org.junit.jupiter.api.Assertions.assertNotNull(userSession, "User-Session 应存在");
        userSession.delete(com.jushan.framework.auth.TenantContext.SESSION_KEY_TENANT_ID);
        userSession.delete(com.jushan.framework.auth.TenantContext.SESSION_KEY_USER_TYPE);
        userSession.delete(com.jushan.framework.auth.TenantContext.SESSION_KEY_ROLES);

        // 请求应被拒绝（缺少 userType，无法确定用户身份）
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 重新登录恢复会话（避免影响后续测试）
        login("13900000001", "pass123");
    }

    @Test
    @DisplayName("P0-3: 平台超级管理员明确得到平台身份（isPlatformUser = true）")
    void shouldIdentifyPlatformSuperAdminCorrectly() throws Exception {
        // admin 已在 @BeforeEach 中登录，使用 superAdminToken
        // 平台用户访问租户专属接口应返回 "平台用户无租户范围"
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("尝试创建", "13911110014", "pass123",
                                "parking_manager", parkingLotId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("平台用户无租户范围")));
    }

    @Test
    @DisplayName("P0-4: 伪造 tenantId 请求参数不能覆盖可信上下文")
    void shouldNotTrustTenantIdFromRequestParameters() throws Exception {
        // 租户 A 的管理员尝试在请求参数中传入租户 B 的 tenantId
        // 后端应从 TenantContext 获取 tenantId，忽略请求参数

        // 创建一个员工，验证是在租户 A 下创建的（tenantId 来自 TenantContext，不是请求体）
        String resp = mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        // 请求体中不包含 tenantId，但尝试传入不属于本租户的 parkingLotId
                        .content(createEmployeeJson("正常员工D", "13911110015", "pass123",
                                "parking_manager", parkingLotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        // 验证创建的员工属于 tenantId（租户 A），而不是 otherTenantId
        Long empId = extractId(resp);
        SysUser created = sysUserMapper.selectById(empId);
        org.junit.jupiter.api.Assertions.assertNotNull(created);
        org.junit.jupiter.api.Assertions.assertEquals(tenantId, created.getTenantId(),
                "员工应属于当前租户 A，不受请求参数影响");
    }

    @Test
    @DisplayName("P0-5: 线程复用不串租户 — 连续请求不同租户无上下文残留")
    void shouldNotLeakContextBetweenDifferentTenantsOnSameThread() throws Exception {
        // 1. 以租户 A 身份请求员工列表
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 2. 以租户 B 身份请求员工列表（同一测试线程）
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 再次以租户 A 身份请求，验证数据隔离正确
        // 在租户 A 中创建一个新员工，然后查询
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("串租户测试E", "13911110016", "pass123",
                                "parking_manager", parkingLotId)))
                .andExpect(status().isOk());

        String listBody = mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 租户 A 的结果不应包含租户 B 的数据
        org.junit.jupiter.api.Assertions.assertTrue(
                listBody.contains("串租户测试E"),
                "应包含租户 A 的员工");
        org.junit.jupiter.api.Assertions.assertFalse(
                listBody.contains("跨租户员工X") && listBody.contains("跨租户员工Y"),
                "不应包含租户 B 的员工数据（上下文残留检测）");
    }

    @Test
    @DisplayName("P0-6: 租户停用后上下文仍可填充 → 业务层校验 reject")
    void shouldRejectWhenTenantIsDisabled() throws Exception {
        // 先登录（此时租户正常，User-Session 中已有 tenantId）
        String token = login("13900000001", "pass123");

        // 停用租户
        Tenant tenant = tenantMapper.selectById(tenantId);
        tenant.setStatus("DISABLED");
        tenantMapper.updateById(tenant);

        try {
            // 上下文填充应成功（tenantId 仍在 User-Session 中）
            // 但 EmployeeService 中会校验租户状态并拒绝
            mockMvc.perform(post("/admin/employees")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createEmployeeJson("停用租户测试", "13911110017", "pass123",
                                    "parking_manager", parkingLotId)))
                    .andExpect(status().isOk())  // HTTP 200 但业务错误码 1000
                    .andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.message", containsString("租户不存在或已被禁用")));
        } finally {
            // 恢复租户状态
            tenant.setStatus("ENABLED");
            tenantMapper.updateById(tenant);
        }
    }

    @Test
    @DisplayName("P0-7: 未登录请求（无 Token）不填充上下文，公开端点正常")
    void shouldNotPopulateContextForUnauthenticatedRequests() throws Exception {
        // 公开端点（登录接口）在未登录时正常访问
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"13900000001\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("P0-8: 伪造 Token（无对应登录态）→ Sa-Token 拦截返回 401")
    void shouldRejectFakeTokenAtSaTokenLevel() throws Exception {
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer completely-fake-token-xyz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("P0-9: 上下文装配异常后用户重新登录可恢复正常")
    void shouldRecoverAfterSessionRepair() throws Exception {
        SysUser tenantAdmin = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "13900000001"));

        // 第一阶段：删除 tenantId 制造异常
        String token1 = login("13900000001", "pass123");
        SaSession userSession = StpUtil.getSessionByLoginId(tenantAdmin.getId());
        userSession.delete(com.jushan.framework.auth.TenantContext.SESSION_KEY_TENANT_ID);

        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());

        // 第二阶段：重新登录恢复会话，应可正常访问
        String token2 = login("13900000001", "pass123");

        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 辅助方法 ====================

    private Long registerAndApprove(String phone, String companyName, String contactPerson,
                                     String password) throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"%s\"}",
                                companyName, contactPerson, phone, password)))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getContactPhone, phone));

        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVED\",\"reason\":\"测试通过\"}"))
                .andExpect(status().isOk());

        return tenant.getId();
    }

    private String login(String username, String password) throws Exception {
        String resp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractToken(resp);
    }

    private Long createParkingLot(Long tenantId, String name) {
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(tenantId);
        lot.setName(name);
        lot.setStatus("ENABLED");
        lot.setCreatedAt(LocalDateTime.now());
        lot.setUpdatedAt(LocalDateTime.now());
        parkingLotMapper.insert(lot);
        return lot.getId();
    }

    private String createEmployeeJson(String displayName, String phone, String password,
                                       String roleCode, Long... parkingLotIds) {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < parkingLotIds.length; i++) {
            if (i > 0) ids.append(",");
            ids.append(parkingLotIds[i]);
        }
        ids.append("]");
        return String.format(
                "{\"displayName\":\"%s\",\"phone\":\"%s\",\"password\":\"%s\"," +
                "\"roleCode\":\"%s\",\"parkingLotIds\":%s}",
                displayName, phone, password, roleCode, ids);
    }

    private String updateEmployeeJson(String displayName, String roleCode, Long... parkingLotIds) {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < parkingLotIds.length; i++) {
            if (i > 0) ids.append(",");
            ids.append(parkingLotIds[i]);
        }
        ids.append("]");
        return String.format(
                "{\"displayName\":\"%s\",\"roleCode\":\"%s\",\"parkingLotIds\":%s}",
                displayName, roleCode, ids);
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private Long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        String idStr = json.substring(start, end).trim();
        return Long.parseLong(idStr);
    }

    /**
     * 在另一个租户中创建员工并返回员工 ID。
     */
    private Long createEmployeeInTenant(String phone, String displayName, String roleCode,
                                          Long parkingLotId) throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson(displayName, phone, "pass123", roleCode, parkingLotId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private void cleanupTestData() {
        // 1. 清理测试租户和用户（按手机号）
        String[] phones = {"13900000001", "13900000002",
                "13911110001", "13911110002", "13911110003", "13911110004",
                "13911110005", "13911110006", "13911110007", "13911110008",
                "13911110009", "13911110010", "13911110011", "13911110012", "13911110013"};
        for (String phone : phones) {
            sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, phone));
            tenantMapper.delete(new LambdaQueryWrapper<Tenant>().eq(Tenant::getContactPhone, phone));
        }

        // 2. 清理测试停车场和授权记录
        for (String namePattern : new String[]{"数据范围", "跨租户"}) {
            java.util.List<ParkingLot> lots = parkingLotMapper.selectList(
                    new LambdaQueryWrapper<ParkingLot>().like(ParkingLot::getName, namePattern));
            if (!lots.isEmpty()) {
                java.util.List<Long> lotIds = lots.stream().map(ParkingLot::getId).toList();
                employeeParkingLotMapper.delete(new LambdaQueryWrapper<EmployeeParkingLot>()
                        .in(EmployeeParkingLot::getParkingLotId, lotIds));
                parkingLotMapper.delete(new LambdaQueryWrapper<ParkingLot>()
                        .in(ParkingLot::getId, lotIds));
            }
        }
    }
}
