package com.jushan.boot.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 客户员工与停车场授权集成测试（T15）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>客户管理员创建员工</li>
 *   <li>角色限制（仅限子角色）</li>
 *   <li>停车场授权校验</li>
 *   <li>员工列表/详情</li>
 *   <li>员工更新</li>
 *   <li>密码重置</li>
 *   <li>启用/禁用</li>
 *   <li>跨租户拒绝</li>
 *   <li>非客户管理员拒绝</li>
 *   <li>参数校验</li>
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
@DisplayName("客户员工与停车场授权集成测试")
class EmployeeIntegrationTest extends TestcontainersBaseTest {

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

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private Long tenantId;
    private Long parkingLotId1;
    private Long parkingLotId2;
    /** 另一个租户的管理员 token（用于跨租户测试） */
    private String otherAdminToken;
    private Long otherTenantId;
    private Long otherParkingLotId;

    @BeforeEach
    void setUp() throws Exception {
        // 1. 超级管理员登录
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = extractToken(responseBody);

        // 2. 清理测试数据
        cleanupTestData();

        // 3. 注册并审核通过一个客户（本租户）
        tenantId = registerAndApprove("13811110001", "测试租户有限公司", "客户管理员张三", "pass123");

        // 4. 客户管理员登录
        customerAdminToken = login("13811110001", "pass123");

        // 5. 创建本租户的停车场
        parkingLotId1 = createParkingLot(tenantId, "测试停车场A");
        parkingLotId2 = createParkingLot(tenantId, "测试停车场B");

        // 6. 创建另一个租户及其停车场（用于跨租户测试）
        otherTenantId = registerAndApprove("13811110002", "其他租户有限公司", "其他管理员", "pass123");
        otherAdminToken = login("13811110002", "pass123");
        otherParkingLotId = createParkingLot(otherTenantId, "其他停车场X");
    }

    // ==================== ① 创建员工 ====================

    @Test
    @DisplayName("正常创建员工 → 成功并返回脱敏手机号")
    void shouldCreateEmployee() throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("员工张三", "13822220001", "pass123",
                                "parking_manager", parkingLotId1, parkingLotId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.phone").value("138****0001"))
                .andExpect(jsonPath("$.data.displayName").value("员工张三"))
                .andExpect(jsonPath("$.data.roleCode").value("parking_manager"))
                .andExpect(jsonPath("$.data.roleName").value("停车场管理员"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.parkingLotIds.length()").value(2))
                .andExpect(jsonPath("$.data.parkingLotNames[0]").value("测试停车场A"))
                .andReturn().getResponse().getContentAsString();

        // 验证数据库中的员工
        Long employeeId = extractId(resp);
        SysUser employee = sysUserMapper.selectById(employeeId);
        assertThat(employee).isNotNull();
        assertThat(employee.getTenantId()).isEqualTo(tenantId);
        assertThat(employee.getStatus()).isEqualTo("ENABLED");
        assertThat(employee.getRoles()).contains("parking_manager");

        // 验证停车场授权
        Long authCount = employeeParkingLotMapper.selectCount(
                new LambdaQueryWrapper<EmployeeParkingLot>()
                        .eq(EmployeeParkingLot::getEmployeeId, employeeId));
        assertThat(authCount).isEqualTo(2);
    }

    @Test
    @DisplayName("创建财务人员 → 成功")
    void shouldCreateFinanceEmployee() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("财务王五", "13822220002", "pass123",
                                "finance", parkingLotId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("finance"))
                .andExpect(jsonPath("$.data.roleName").value("财务"));
    }

    @Test
    @DisplayName("创建设备运维人员 → 成功")
    void shouldCreateDeviceMaintenanceEmployee() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("运维赵六", "13822220003", "pass123",
                                "device_maintenance", parkingLotId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("device_maintenance"));
    }

    @Test
    @DisplayName("创建岗亭人员 → 成功")
    void shouldCreateBoothOperator() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("岗亭孙七", "13822220004", "pass123",
                                "booth_operator", parkingLotId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("booth_operator"));
    }

    // ==================== ② 创建拒绝场景 ====================

    @Test
    @DisplayName("重复手机号 → 拒绝")
    void shouldRejectDuplicatePhone() throws Exception {
        // 第一次创建成功
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("员工A", "13822220005", "pass123",
                        "parking_manager", parkingLotId1)))
                .andExpect(status().isOk());

        // 第二次用相同手机号 → 拒绝
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("员工B", "13822220005", "pass123",
                                "booth_operator", parkingLotId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("该手机号已被使用"));
    }

    @Test
    @DisplayName("不允许的角色 → 拒绝")
    void shouldRejectInvalidRole() throws Exception {
        // customer_admin 不能分配给员工（PARAM_ERROR code=400 → HTTP 400）
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("员工C", "13822220006", "pass123",
                                "customer_admin", parkingLotId1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("不支持的角色: customer_admin")));

        // super_admin 不能分配（PARAM_ERROR → HTTP 400）
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("员工D", "13822220007", "pass123",
                                "super_admin", parkingLotId1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("跨租户停车场 → 拒绝")
    void shouldRejectCrossTenantParkingLot() throws Exception {
        // FORBIDDEN code=403 → HTTP 403
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("员工E", "13822220008", "pass123",
                                "parking_manager", otherParkingLotId))) // 其他租户的停车场
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("停车场 '其他停车场X' 不属于本租户"));
    }

    @Test
    @DisplayName("空停车场列表 → 拒绝")
    void shouldRejectEmptyParkingLots() throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"员工F\",\"phone\":\"13822220009\"," +
                                "\"password\":\"pass123\",\"roleCode\":\"parking_manager\",\"parkingLotIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("参数校验失败 → 400")
    void shouldRejectInvalidParams() throws Exception {
        // 空姓名
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("", "13822220010", "pass123",
                                "parking_manager", parkingLotId1)))
                .andExpect(status().isBadRequest());

        // 非法手机号
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("员工G", "12345", "pass123",
                                "parking_manager", parkingLotId1)))
                .andExpect(status().isBadRequest());

        // 短密码
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("员工H", "13822220011", "ab",
                                "parking_manager", parkingLotId1)))
                .andExpect(status().isBadRequest());
    }

    // ==================== ③ 员工列表与详情 ====================

    @Test
    @DisplayName("查询员工列表 → 仅返回本租户员工")
    void shouldListEmployeesInTenant() throws Exception {
        // 创建两个员工
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("列表员工1", "13822230001", "pass123",
                        "parking_manager", parkingLotId1)));
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("列表员工2", "13822230002", "pass123",
                        "booth_operator", parkingLotId1, parkingLotId2)));

        // 在另一租户也创建员工
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + otherAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("其他员工", "13822230003", "pass123",
                        "parking_manager", otherParkingLotId)));

        // 本租户管理员只能看到自己的员工
        mockMvc.perform(get("/admin/employees?page=1&size=20")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("查询员工详情 → 含授权停车场信息")
    void shouldGetEmployeeDetail() throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("详情员工", "13822240001", "pass123",
                        "parking_manager", parkingLotId1, parkingLotId2)))
                .andReturn().getResponse().getContentAsString();
        Long employeeId = extractId(resp);

        mockMvc.perform(get("/admin/employees/" + employeeId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("详情员工"))
                .andExpect(jsonPath("$.data.phone").value("138****0001"))
                .andExpect(jsonPath("$.data.parkingLotIds.length()").value(2))
                .andExpect(jsonPath("$.data.parkingLotNames[0]").value("测试停车场A"));
    }

    @Test
    @DisplayName("查看另一租户员工详情 → 403")
    void shouldRejectCrossTenantDetail() throws Exception {
        // 在另一租户创建员工
        String resp = mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + otherAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("跨租户员工", "13822240002", "pass123",
                        "parking_manager", otherParkingLotId)))
                .andReturn().getResponse().getContentAsString();
        Long otherEmployeeId = extractId(resp);

        // 本租户管理员查询另一租户的员工 → 403
        mockMvc.perform(get("/admin/employees/" + otherEmployeeId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("员工不属于本租户"));
    }

    // ==================== ④ 更新员工 ====================

    @Test
    @DisplayName("更新员工角色和停车场 → 成功")
    void shouldUpdateEmployee() throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("原始员工", "13822250001", "pass123",
                        "parking_manager", parkingLotId1)))
                .andReturn().getResponse().getContentAsString();
        Long employeeId = extractId(resp);

        // 更新角色和停车场
        mockMvc.perform(put("/admin/employees/" + employeeId)
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateEmployeeJson("更新后员工", "booth_operator", parkingLotId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("更新后员工"))
                .andExpect(jsonPath("$.data.roleCode").value("booth_operator"))
                .andExpect(jsonPath("$.data.parkingLotIds.length()").value(1))
                .andExpect(jsonPath("$.data.parkingLotIds[0]").value(parkingLotId2));

        // 验证数据库
        SysUser updated = sysUserMapper.selectById(employeeId);
        assertThat(updated.getDisplayName()).isEqualTo("更新后员工");
        assertThat(updated.getRoles()).contains("booth_operator");
        Long authCount = employeeParkingLotMapper.selectCount(
                new LambdaQueryWrapper<EmployeeParkingLot>()
                        .eq(EmployeeParkingLot::getEmployeeId, employeeId));
        assertThat(authCount).isEqualTo(1);
    }

    // ==================== ⑤ 密码重置 ====================

    @Test
    @DisplayName("重置员工密码 → 登录成功")
    void shouldResetPassword() throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("密码员工", "13822260001", "oldpass",
                        "parking_manager", parkingLotId1)))
                .andReturn().getResponse().getContentAsString();
        Long employeeId = extractId(resp);

        // 重置密码
        mockMvc.perform(post("/admin/employees/" + employeeId + "/reset-password")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 用新密码登录
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"13822260001\",\"password\":\"newpass123\"}"))
                .andExpect(status().isOk());

        // 旧密码失效
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"13822260001\",\"password\":\"oldpass\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== ⑥ 启停 ====================

    @Test
    @DisplayName("禁用员工 → 状态变为 DISABLED")
    void shouldDisableEmployee() throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("禁用员工", "13822270001", "pass123",
                        "finance", parkingLotId1)))
                .andReturn().getResponse().getContentAsString();
        Long employeeId = extractId(resp);

        mockMvc.perform(post("/admin/employees/" + employeeId + "/status?action=DISABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        SysUser disabled = sysUserMapper.selectById(employeeId);
        assertThat(disabled.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("重新启用员工 → 状态变为 ENABLED")
    void shouldReEnableEmployee() throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("恢复员工", "13822270002", "pass123",
                        "finance", parkingLotId1)))
                .andReturn().getResponse().getContentAsString();
        Long employeeId = extractId(resp);

        // 先禁用
        mockMvc.perform(post("/admin/employees/" + employeeId + "/status?action=DISABLED")
                .header("Authorization", "Bearer " + customerAdminToken));

        // 再启用
        mockMvc.perform(post("/admin/employees/" + employeeId + "/status?action=ENABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk());

        SysUser enabled = sysUserMapper.selectById(employeeId);
        assertThat(enabled.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("重复禁用 → 拒绝")
    void shouldRejectDoubleDisable() throws Exception {
        String resp = mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("双禁员工", "13822270003", "pass123",
                        "finance", parkingLotId1)))
                .andReturn().getResponse().getContentAsString();
        Long employeeId = extractId(resp);

        // 第一次禁用成功
        mockMvc.perform(post("/admin/employees/" + employeeId + "/status?action=DISABLED")
                .header("Authorization", "Bearer " + customerAdminToken));

        // 第二次禁用拒绝
        mockMvc.perform(post("/admin/employees/" + employeeId + "/status?action=DISABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("员工已是禁用状态"));
    }

    // ==================== ⑦ 权限校验 ====================

    @Test
    @DisplayName("非客户管理员创建员工 → 403")
    void shouldRejectNonCustomerAdmin() throws Exception {
        // 先创建一个 parking_manager 员工（供后续登录使用）
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("普通员工", "13822280001", "pass123",
                        "parking_manager", parkingLotId1)))
                .andExpect(status().isOk());

        // parking_manager 登录
        String pmToken = login("13822280001", "pass123");

        // parking_manager 尝试创建员工（@SaCheckPermission 拦截 → NotPermissionException → "无权限访问"）
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("越权员工", "13822280002", "pass123",
                                "booth_operator", parkingLotId1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权限访问"));
    }

    @Test
    @DisplayName("未登录访问 → 401")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/employees"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== ⑧ 员工数量上限 ====================

    @Test
    @DisplayName("员工数量达上限 → 拒绝")
    void shouldRejectWhenMaxEmployeesReached() throws Exception {
        // 修改租户的 max_employees 上限：当前已有1个(customer_admin)，上限设为2
        tenantMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Tenant>()
                        .set(com.jushan.system.entity.Tenant::getMaxEmployees, 2)
                        .eq(com.jushan.system.entity.Tenant::getId, tenantId));

        // 创建第一个员工 → 成功（总数变为2）
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createEmployeeJson("第一个员工", "13822300001", "pass123",
                        "parking_manager", parkingLotId1)))
                .andExpect(status().isOk());

        // 创建第二个员工 → 拒绝（达上限）
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEmployeeJson("第二个员工", "13822300002", "pass123",
                                "booth_operator", parkingLotId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("员工数量已达上限（2人）"));
    }

    // ==================== 工具方法 ====================

    /**
     * 注册并审核通过，返回 tenantId。
     */
    private Long registerAndApprove(String phone, String companyName, String contactPerson,
                                     String password) throws Exception {
        // 注册
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"%s\"}",
                                companyName, contactPerson, phone, password)))
                .andExpect(status().isOk());

        // 查找 tenant
        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, phone));

        // 审核通过
        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVED\",\"reason\":\"测试通过\"}"));

        return tenant.getId();
    }

    /**
     * 登录并返回 Token。
     */
    private String login(String username, String password) throws Exception {
        String resp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractToken(resp);
    }

    /**
     * 创建停车场（直接通过 Mapper），返回 parkingLotId。
     */
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

    /**
     * 构建创建员工 JSON。
     */
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

    /**
     * 构建更新员工 JSON。
     */
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

    /**
     * 从 JSON 响应中提取 Token。
     */
    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    /**
     * 从 JSON 响应中提取 data.id。
     */
    private Long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        String idStr = json.substring(start, end).trim();
        return Long.parseLong(idStr);
    }

    /**
     * 清理测试数据。
     */
    private void cleanupTestData() {
        // 1. 清理测试用户和租户（按手机号）
        String[] phones = {"13811110001", "13811110002", "13822220001", "13822220002",
                "13822220003", "13822220004", "13822220005", "13822220006", "13822220007",
                "13822220008", "13822220009", "13822220010", "13822220011",
                "13822230001", "13822230002", "13822230003",
                "13822240001", "13822240002",
                "13822250001",
                "13822260001",
                "13822270001", "13822270002", "13822270003",
                "13822280001", "13822280002",
                "13822300001", "13822300002"};
        for (String phone : phones) {
            sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, phone));
            tenantMapper.delete(new LambdaQueryWrapper<Tenant>().eq(Tenant::getContactPhone, phone));
        }

        // 2. 清理测试停车场和授权记录
        for (String namePattern : new String[]{"测试", "其他", "跨租户"}) {
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
