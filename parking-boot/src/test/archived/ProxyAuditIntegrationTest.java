package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.SysAuditLog;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.SysAuditLogMapper;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 超级管理员代操作与高风险审计集成测试（T17）。
 * <p>
 * 验证：
 * <ul>
 *   <li>超级管理员启动/停止代操作模式</li>
 *   <li>非平台用户启动代理被拒绝</li>
 *   <li>已禁用租户不允许代理</li>
 *   <li>代理模式下 TenantContext 正确切换租户上下文</li>
 *   <li>/auth/session 返回代理状态</li>
 *   <li>审计日志记录完整（proxy_start/proxy_stop）</li>
 *   <li>审计日志查询的数据范围控制</li>
 *   <li>代理模式下不能再次启动代理</li>
 *   <li>非代理模式调用 stopProxy 被拒绝</li>
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
@DisplayName("超级管理员代操作与高风险审计集成测试")
class ProxyAuditIntegrationTest extends TestcontainersBaseTest {

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
    private SysAuditLogMapper sysAuditLogMapper;

    private static final String SUPER_ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_PASSWORD = "admin123";

    private String superAdminToken;
    private Long tenantId;
    private String tenantAdminToken;
    private Long disabledTenantId;

    // ==================== 生命周期 ====================

    @BeforeEach
    void setUp() throws Exception {
        // 1. 清理
        cleanupTestData();

        // 2. 超级管理员登录
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + SUPER_ADMIN_USERNAME + "\",\"password\":\"" + SUPER_ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        superAdminToken = extractToken(responseBody);

        // 3. 创建启用租户
        tenantId = registerAndApprove("13920000001", "代操作测试有限公司", "测试管理员", "pass123");
        tenantAdminToken = login("13920000001", "pass123");

        // 4. 创建已禁用租户
        disabledTenantId = registerAndApprove("13920000002", "已禁用测试有限公司", "禁用管理员", "pass123");
        // 禁用该租户
        mockMvc.perform(post("/admin/tenants/" + disabledTenantId + "/audit")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\",\"reason\":\"测试禁用\"}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        // cleanupTestData 已在 @BeforeEach 调用
    }

    // ==================== ① 启动代操作 ====================

    @Test
    @DisplayName("超级管理员启动代操作 → 成功，/auth/session 返回 isProxy=true")
    void shouldStartProxySuccessfully() throws Exception {
        // 启动代操作
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"代客户配置停车场\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证 session 返回代操作状态
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.proxy.isProxy").value(true))
                .andExpect(jsonPath("$.data.proxy.proxyTargetTenantId").value(tenantId.intValue()))
                .andExpect(jsonPath("$.data.tenantId").value(tenantId.intValue()));

        // 清理：退出代操作
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));
    }

    @Test
    @DisplayName("超级管理员启动代操作 → 审计日志记录了 proxy_start")
    void shouldLogProxyStartInAudit() throws Exception {
        // 启动代操作
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"审计测试原因\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 查询审计日志
        mockMvc.perform(get("/admin/audit-logs?page=1&size=10&action=proxy_start")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("proxy_start"))
                .andExpect(jsonPath("$.data.records[0].isProxy").value(1))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].reason").value("审计测试原因"));

        // 清理
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));
    }

    // ==================== ② 停止代操作 ====================

    @Test
    @DisplayName("停止代操作 → 成功，/auth/session 返回 isProxy=false")
    void shouldStopProxySuccessfully() throws Exception {
        // 先启动
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"测试\"}"))
                .andExpect(status().isOk());

        // 停止
        mockMvc.perform(post("/admin/proxy/stop")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证 session 恢复正常
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proxy.isProxy").value(false))
                .andExpect(jsonPath("$.data.tenantId").doesNotExist());
    }

    @Test
    @DisplayName("停止代操作 → 审计日志记录了 proxy_stop")
    void shouldLogProxyStopInAudit() throws Exception {
        // 先启动
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"测试\"}"))
                .andExpect(status().isOk());

        // 停止
        mockMvc.perform(post("/admin/proxy/stop")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());

        // 查询审计日志
        mockMvc.perform(get("/admin/audit-logs?page=1&size=10&action=proxy_stop")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("proxy_stop"))
                .andExpect(jsonPath("$.data.records[0].isProxy").value(1));
    }

    // ==================== ③ 越权与异常路径 ====================

    @Test
    @DisplayName("非平台用户（租户管理员）启动代操作被拒绝 → FORBIDDEN")
    void shouldRejectNonPlatformUserFromProxy() throws Exception {
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + tenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"尝试代理\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("无权限")));
    }

    @Test
    @DisplayName("目标租户不存在 → NOT_FOUND")
    void shouldRejectNonExistentTenant() throws Exception {
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":99999,\"reason\":\"不存在的租户\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("目标租户已禁用 → BUSINESS_ERROR")
    void shouldRejectDisabledTenant() throws Exception {
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + disabledTenantId + ",\"reason\":\"已禁用的租户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("租户不存在或已被禁用")));
    }

    @Test
    @DisplayName("重复启动代操作被拒绝 → BUSINESS_ERROR")
    void shouldRejectDoubleProxyStart() throws Exception {
        // 第一次启动
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"第一次\"}"))
                .andExpect(status().isOk());

        // 第二次启动
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"第二次\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已是代操作模式")));

        // 清理
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));
    }

    @Test
    @DisplayName("非代理模式调用 stopProxy → BUSINESS_ERROR")
    void shouldRejectStopProxyWhenNotInProxyMode() throws Exception {
        mockMvc.perform(post("/admin/proxy/stop")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("未处于代操作模式")));
    }

    @Test
    @DisplayName("未认证请求无法启动代操作 → 401")
    void shouldRejectUnauthenticatedProxyStart() throws Exception {
        mockMvc.perform(post("/admin/proxy/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"未登录\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("启动代操作原因不能为空 → BAD_REQUEST")
    void shouldRejectEmptyReason() throws Exception {
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== ④ 代理模式下数据上下文切换 ====================

    @Test
    @DisplayName("代理启动后 → /admin/proxy/status 返回 isProxy=true")
    void shouldReturnProxyStatusAfterStart() throws Exception {
        // 启动代操作
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"状态检查\"}"))
                .andExpect(status().isOk());

        // 获取状态
        mockMvc.perform(get("/admin/proxy/status")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.isProxy").value(true))
                .andExpect(jsonPath("$.data.proxyTargetTenantId").value(tenantId.intValue()));

        // 清理
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));
    }

    // ==================== ⑤ 审计日志查询 ====================

    @Test
    @DisplayName("超级管理员查询全平台审计日志 → 返回所有记录")
    void shouldQueryAllAuditLogsForSuperAdmin() throws Exception {
        // 先产生两条审计日志
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"查询测试1\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));

        // 查询全部
        mockMvc.perform(get("/admin/audit-logs?page=1&size=20")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("租户用户默认无审计日志查看权限 → FORBIDDEN")
    void shouldLimitAuditLogsToOwnTenant() throws Exception {
        // 超级管理员对 tenantId 产生一条代理日志
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"租户隔离测试\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));

        // 租户管理员无 tenant:read 权限，无法访问审计日志
        mockMvc.perform(get("/admin/audit-logs?page=1&size=20")
                        .header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("审计日志详情 → 租户用户无权访问审计日志")
    void shouldRejectCrossTenantAuditDetail() throws Exception {
        // 创建另一个租户
        Long otherTenantId = registerAndApprove("13920000003", "其他租户", "其他管理员", "pass123");

        // 超级管理员对其进行代操作
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + otherTenantId + ",\"reason\":\"越权测试\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));

        // 获取该日志 ID
        java.util.List<SysAuditLog> logs = sysAuditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTenantId, otherTenantId)
                        .last("LIMIT 1"));
        Assertions.assertFalse(logs.isEmpty(), "应该有审计日志");
        Long auditLogId = logs.get(0).getId();

        // 租户管理员尝试查看审计日志详情 → 无权限
        mockMvc.perform(get("/admin/audit-logs/" + auditLogId)
                        .header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isForbidden());

        // 清理其他租户数据
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "13920000003"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>().eq(Tenant::getContactPhone, "13920000003"));
    }

    @Test
    @DisplayName("按操作类型筛选审计日志")
    void shouldFilterByAction() throws Exception {
        // 先产生两条日志
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"筛选测试\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));

        // 只查 proxy_start
        mockMvc.perform(get("/admin/audit-logs?page=1&size=10&action=proxy_start")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].action").value("proxy_start"));
    }

    @Test
    @DisplayName("按是否代操作筛选 → isProxy=1")
    void shouldFilterByIsProxy() throws Exception {
        // 先产生一条代操作日志
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"isProxy筛选\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));

        mockMvc.perform(get("/admin/audit-logs?page=1&size=10&isProxy=1")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.records[0].isProxy").value(1));
    }

    @Test
    @DisplayName("审计日志详情 → 正常查询")
    void shouldGetAuditLogDetail() throws Exception {
        // 先产生一条日志
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"详情查询测试\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken));

        // 获取日志 ID
        java.util.List<SysAuditLog> logs = sysAuditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTenantId, tenantId)
                        .eq(SysAuditLog::getAction, "proxy_start")
                        .last("LIMIT 1"));
        Assertions.assertFalse(logs.isEmpty());
        Long auditLogId = logs.get(0).getId();

        // 查询详情
        mockMvc.perform(get("/admin/audit-logs/" + auditLogId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("proxy_start"))
                .andExpect(jsonPath("$.data.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.reason").value("详情查询测试"));
    }

    @Test
    @DisplayName("审计日志不存在 → NOT_FOUND")
    void shouldReturnNotFoundForMissingAuditLog() throws Exception {
        mockMvc.perform(get("/admin/audit-logs/99999")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isNotFound());
    }

    // ==================== ⑥ 代理状态恢复 ====================

    @Test
    @DisplayName("代理停止后 platform 用户恢复原始上下文")
    void shouldRestorePlatformContextAfterProxyStop() throws Exception {
        // 启动代理
        mockMvc.perform(post("/admin/proxy/start")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + ",\"reason\":\"恢复测试\"}"))
                .andExpect(status().isOk());

        // 代理模式下 session 包含 tenantId
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(tenantId.intValue()));

        // 停止代理
        mockMvc.perform(post("/admin/proxy/stop")
                .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());

        // 恢复后 session 不包含 tenantId（平台用户）
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.proxy.isProxy").value(false));
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
        Assertions.assertNotNull(tenant, "租户应已创建，phone=" + phone);

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

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private void cleanupTestData() {
        String[] phones = {"13920000001", "13920000002", "13920000003"};
        for (String phone : phones) {
            sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, phone));
            tenantMapper.delete(new LambdaQueryWrapper<Tenant>().eq(Tenant::getContactPhone, phone));
        }
        // 清理审计日志中的测试数据
        sysAuditLogMapper.delete(new LambdaQueryWrapper<SysAuditLog>()
                .in(SysAuditLog::getAction, java.util.List.of("proxy_start", "proxy_stop")));
    }
}
