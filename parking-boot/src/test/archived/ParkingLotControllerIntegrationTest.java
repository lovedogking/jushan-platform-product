package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.Company;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingLotCapacityLog;
import com.jushan.system.entity.ParkingLotStatusLog;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.CompanyMapper;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotCapacityLogMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingLotStatusLogMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 停车场基础信息、状态与容量集成测试（T18）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>创建停车场（租户隔离、参数校验）</li>
 *   <li>查询停车场列表和详情（租户隔离）</li>
 *   <li>更新停车场基础信息</li>
 *   <li>启用/停用（权限区分：customer_admin 可停用，parking_manager 不可）</li>
 *   <li>修改总车位数（审计）</li>
 *   <li>人工修正剩余车位数（审计）</li>
 *   <li>跨租户拒绝</li>
 *   <li>并发更新保护</li>
 *   <li>负数边界</li>
 *   <li>状态流转校验</li>
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
@DisplayName("停车场基础信息、状态与容量集成测试")
class ParkingLotControllerIntegrationTest extends TestcontainersBaseTest {

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
    private ParkingLotCapacityLogMapper capacityLogMapper;

    @Autowired
    private ParkingLotStatusLogMapper statusLogMapper;

    @Autowired
    private ParkingLaneMapper laneMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private CompanyMapper companyMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private Long tenantId;
    private Long defaultCompanyId;

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

        // 3. 注册并审核通过客户
        tenantId = registerAndApprove("13900000001", "T18测试租户有限公司", "客户管理员");

        // 4. 获取租户默认集团，后续创建停车场需要 company_id
        Company defaultCompany = companyMapper.selectDefaultByTenantIdIgnoreTenant(tenantId);
        assertThat(defaultCompany).isNotNull();
        defaultCompanyId = defaultCompany.getId();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ==================== ① 创建停车场 ====================

    @Test
    @DisplayName("正常创建停车场 → 总车位 = 剩余车位，初始状态 DISABLED（FIX-08）")
    void shouldCreateParkingLot() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        String resp = mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试停车场A\",\"address\":\"测试地址1号\"," +
                                "\"contactPhone\":\"010-12345678\",\"totalSpaces\":100,\"companyId\":" + defaultCompanyId + "," +
                                "\"paymentMode\":\"PLATFORM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("测试停车场A"))
                .andExpect(jsonPath("$.data.totalSpaces").value(100))
                .andExpect(jsonPath("$.data.currentVehicles").value(0))
                .andExpect(jsonPath("$.data.remainingSpaces").value(100))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.tenantId").value(tenantId))
                .andReturn().getResponse().getContentAsString();

        // 验证数据库
        Long lotId = extractId(resp);
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(lot).isNotNull();
        assertThat(lot.getTenantId()).isEqualTo(tenantId);
        assertThat(lot.getTotalSpaces()).isEqualTo(100);
        assertThat(lot.getRemainingSpaces()).isEqualTo(100);
        assertThat(lot.getCurrentVehicles()).isEqualTo(0);
        assertThat(lot.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("创建停车场使用默认值 → 各配置字段为默认值")
    void shouldCreateWithDefaults() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"默认配置停车场\",\"totalSpaces\":50,\"companyId\":" + defaultCompanyId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMode").value("PLATFORM"))
                .andExpect(jsonPath("$.data.imageRetentionDays").value(30))
                .andExpect(jsonPath("$.data.dataRetentionDays").value(365))
                .andExpect(jsonPath("$.data.freeExitMinutes").value(15))
                .andExpect(jsonPath("$.data.manualReleasePolicy").value("ADMIN_ONLY"))
                .andExpect(jsonPath("$.data.offlinePolicy").value("ALLOW_ENTRY_EXIT"));
    }

    @Test
    @DisplayName("总车位为 0 → 创建成功（允许零车位场景）")
    void shouldCreateWithZeroSpaces() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"零车位停车场\",\"totalSpaces\":0,\"companyId\":" + defaultCompanyId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpaces").value(0))
                .andExpect(jsonPath("$.data.remainingSpaces").value(0));
    }

    @Test
    @DisplayName("总车位为负数 → 400 参数校验失败")
    void shouldRejectNegativeTotalSpaces() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"负数停车场\",\"totalSpaces\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("停车场名称为空 → 400")
    void shouldRejectEmptyName() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"totalSpaces\":100}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== ② 查询停车场 ====================

    @Test
    @DisplayName("查询停车场列表 → 仅返回本租户停车场")
    void shouldListParkingLotsInTenant() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        // 创建两个停车场
        createParkingLotViaApi(customerAdminToken, "列表停车场A", 100);
        createParkingLotViaApi(customerAdminToken, "列表停车场B", 200);

        // 创建另一个租户和停车场
        Long otherTenantId = registerAndApprove("13900000002", "其他租户", "其他管理员");
        String otherToken = login("13900000002", "pass123");
        Long otherCompanyId = companyMapper.selectDefaultByTenantIdIgnoreTenant(otherTenantId).getId();
        createParkingLotViaApi(otherToken, otherCompanyId, "其他租户停车场", 300);

        // 本租户只能看到自己的
        mockMvc.perform(get("/admin/parking-lots?page=1&size=20")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("按状态筛选 → 仅返回匹配状态")
    void shouldFilterByStatus() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        // FIX-08：新停车场默认 DISABLED，直接插入 ENABLED 以测试状态筛选
        createParkingLotInDb("启用停车场", 100, "ENABLED");
        Long lotId2 = createParkingLotViaApi(customerAdminToken, "待停用停车场", 200);

        // 停用第二个
        mockMvc.perform(post("/admin/parking-lots/" + lotId2 + "/status")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\",\"reason\":\"测试停用\"}"));

        // 按 ENABLED 筛选
        mockMvc.perform(get("/admin/parking-lots?page=1&size=20&status=ENABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("启用停车场"));

        // 按 DISABLED 筛选
        mockMvc.perform(get("/admin/parking-lots?page=1&size=20&status=DISABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("待停用停车场"));
    }

    @Test
    @DisplayName("查询停车场详情 → 返回完整信息")
    void shouldGetParkingLotDetail() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "详情停车场", 150);

        mockMvc.perform(get("/admin/parking-lots/" + lotId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("详情停车场"))
                .andExpect(jsonPath("$.data.totalSpaces").value(150))
                .andExpect(jsonPath("$.data.remainingSpaces").value(150));
    }

    @Test
    @DisplayName("跨租户查询停车场详情 → 403")
    void shouldRejectCrossTenantDetail() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        // 创建另一个租户的停车场
        Long otherTenantId = registerAndApprove("13900000003", "跨查租户", "跨查管理员");
        String otherToken = login("13900000003", "pass123");
        Long otherCompanyId = companyMapper.selectDefaultByTenantIdIgnoreTenant(otherTenantId).getId();
        Long otherLotId = createParkingLotViaApi(otherToken, otherCompanyId, "他人停车场", 100);

        // 本租户管理员查询 → 403
        mockMvc.perform(get("/admin/parking-lots/" + otherLotId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    // ==================== ③ 更新停车场基础信息 ====================

    @Test
    @DisplayName("更新停车场名称和地址 → 成功")
    void shouldUpdateParkingLot() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "原始名称", 100);

        mockMvc.perform(put("/admin/parking-lots/" + lotId)
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\",\"address\":\"新地址\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名称"))
                .andExpect(jsonPath("$.data.address").value("新地址"))
                .andExpect(jsonPath("$.data.totalSpaces").value(100)); // 未修改字段不变

        // 验证数据库
        ParkingLot updated = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(updated.getName()).isEqualTo("新名称");
        assertThat(updated.getAddress()).isEqualTo("新地址");
        assertThat(updated.getTotalSpaces()).isEqualTo(100);
    }

    @Test
    @DisplayName("部分更新 → 仅更新非 null 字段")
    void shouldPartialUpdate() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "部分更新", 100);

        // 只更新 freeExitMinutes
        mockMvc.perform(put("/admin/parking-lots/" + lotId)
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freeExitMinutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("部分更新")) // 未变
                .andExpect(jsonPath("$.data.freeExitMinutes").value(30));
    }

    // ==================== ④ 启用/停用 ====================

    @Test
    @DisplayName("客户管理员停用停车场 → 成功并写入审计")
    void shouldDisableByCustomerAdmin() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        // FIX-08：新停车场默认 DISABLED，此处直接插入 ENABLED 以测试停用流程
        Long lotId = createParkingLotInDb("可停用停车场", 100, "ENABLED");

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\",\"reason\":\"设备维护\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证数据库
        ParkingLot disabled = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(disabled.getStatus()).isEqualTo("DISABLED");

        // 验证审计日志
        Long logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM parking_lot_status_log WHERE parking_lot_id = ?",
                Long.class, lotId);
        assertThat(logCount).isEqualTo(1);
    }

    @Test
    @DisplayName("重新启用停车场 → 就绪检查通过后成功启用")
    void shouldReEnableParkingLot() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        // FIX-08：启用需要就绪检查，创建已配置车道的停车场
        Long lotId = setupReadyParkingLot("可启用停车场", 100);

        // 先停用
        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\",\"reason\":\"测试停用\"}"));

        // 再启用
        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLED\",\"reason\":\"测试启用\"}"))
                .andExpect(status().isOk());

        ParkingLot enabled = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(enabled.getStatus()).isEqualTo("ENABLED");

        // 审计日志应有两条记录
        Long logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM parking_lot_status_log WHERE parking_lot_id = ?",
                Long.class, lotId);
        assertThat(logCount).isEqualTo(2);
    }

    @Test
    @DisplayName("重复停用 → 拒绝")
    void shouldRejectDoubleDisable() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "双禁停车场", 100);

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\",\"reason\":\"第一次\"}"));

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\",\"reason\":\"第二次\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("停车场已是停用状态"));
    }

    @Test
    @DisplayName("停用未填写原因 → 400")
    void shouldRejectDisableWithoutReason() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "无原因停车场", 100);

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("停车场管理员尝试停用 → 403（T18 停止条件）")
    void shouldRejectDisableByParkingManager() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        // 创建停车场
        Long lotId = createParkingLotViaApi(customerAdminToken, "管理员不可停用", 100);

        // 创建一个 parking_manager 员工
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"停车场管理员A\",\"phone\":\"13911110001\"," +
                                "\"password\":\"pass123\",\"roleCode\":\"parking_manager\"," +
                                "\"parkingLotIds\":[" + lotId + "]}"))
                .andExpect(status().isOk());

        String pmToken = login("13911110001", "pass123");

        // parking_manager 尝试停用 → 403（parking_manager 角色没有 parking:disable 权限）
        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\",\"reason\":\"越权停用\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权限访问"));

        // 验证停车场仍为 DISABLED（FIX-08：新停车场默认 DISABLED，未启用）
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(lot.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("停车场管理员尝试启用 → 403")
    void shouldRejectEnableByParkingManager() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "待启停车场", 100);

        // 先由客户管理员停用
        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\",\"reason\":\"测试\"}"));

        // 创建 parking_manager
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"停车场管理员B\",\"phone\":\"13911110002\"," +
                        "\"password\":\"pass123\",\"roleCode\":\"parking_manager\"," +
                        "\"parkingLotIds\":[" + lotId + "]}"));

        String pmToken = login("13911110002", "pass123");

        // parking_manager 尝试启用 → 403
        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/status")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLED\",\"reason\":\"越权启用\"}"))
                .andExpect(status().isForbidden());

        // 验证仍为 DISABLED
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(lot.getStatus()).isEqualTo("DISABLED");
    }

    // ==================== ⑤ 容量管理 ====================

    @Test
    @DisplayName("修改总车位数 → 成功并记录审计")
    void shouldUpdateTotalSpaces() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "扩容停车场", 100);

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/capacity")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"total_spaces\",\"value\":200,\"reason\":\"扩建完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证数据库
        ParkingLot updated = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(updated.getTotalSpaces()).isEqualTo(200);

        // 验证审计日志
        ParkingLotCapacityLog logEntry = jdbcTemplate.query(
                "SELECT field_name, before_value, after_value, reason FROM parking_lot_capacity_log WHERE parking_lot_id = ? AND field_name = ?",
                (rs, rowNum) -> {
                    ParkingLotCapacityLog log = new ParkingLotCapacityLog();
                    log.setFieldName(rs.getString("field_name"));
                    log.setBeforeValue(rs.getInt("before_value"));
                    log.setAfterValue(rs.getInt("after_value"));
                    log.setReason(rs.getString("reason"));
                    return log;
                },
                lotId, "total_spaces").stream().findFirst().orElse(null);
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getBeforeValue()).isEqualTo(100);
        assertThat(logEntry.getAfterValue()).isEqualTo(200);
        assertThat(logEntry.getReason()).isEqualTo("扩建完成");
    }

    @Test
    @DisplayName("人工修正剩余车位数 → 成功并记录审计")
    void shouldManuallyCorrectRemainingSpaces() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "修正停车场", 100);

        // 初始 remainingSpaces = 100（= totalSpaces - currentVehicles）
        // 人工修正为 80
        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/capacity")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"remaining_spaces\",\"value\":80,\"reason\":\"地感计数修正\"}"))
                .andExpect(status().isOk());

        ParkingLot updated = parkingLotMapper.selectByIdIgnoreTenant(lotId);
        assertThat(updated.getRemainingSpaces()).isEqualTo(80);

        // 审计日志
        ParkingLotCapacityLog logEntry = jdbcTemplate.query(
                "SELECT field_name, before_value, after_value, reason FROM parking_lot_capacity_log WHERE parking_lot_id = ? AND field_name = ?",
                (rs, rowNum) -> {
                    ParkingLotCapacityLog log = new ParkingLotCapacityLog();
                    log.setFieldName(rs.getString("field_name"));
                    log.setBeforeValue(rs.getInt("before_value"));
                    log.setAfterValue(rs.getInt("after_value"));
                    log.setReason(rs.getString("reason"));
                    return log;
                },
                lotId, "remaining_spaces").stream().findFirst().orElse(null);
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getBeforeValue()).isEqualTo(100);
        assertThat(logEntry.getAfterValue()).isEqualTo(80);
        assertThat(logEntry.getReason()).isEqualTo("地感计数修正");
    }

    @Test
    @DisplayName("容量修改为负数 → 400")
    void shouldRejectNegativeCapacity() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "负数容量", 100);

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/capacity")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"total_spaces\",\"value\":-1,\"reason\":\"负数测试\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("容量修改为无效字段 → 400")
    void shouldRejectInvalidField() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "无效字段", 100);

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/capacity")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"invalid_field\",\"value\":1,\"reason\":\"测试\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("容量修改缺少原因 → 400")
    void shouldRejectCapacityWithoutReason() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        Long lotId = createParkingLotViaApi(customerAdminToken, "缺原因", 100);

        mockMvc.perform(post("/admin/parking-lots/" + lotId + "/capacity")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldName\":\"total_spaces\",\"value\":200}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== ⑥ 权限校验 ====================

    @Test
    @DisplayName("未登录访问 → 401")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/parking-lots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("无权限角色创建停车场 → 403")
    void shouldRejectNoPermission() throws Exception {
        customerAdminToken = login("13900000001", "pass123");

        // 先创建停车场
        Long lotId = createParkingLotViaApi(customerAdminToken, "权限测试停车场", 100);

        // 创建 booth_operator（只有 record:read, order:read, gate:open, gate:manual）
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"岗亭员工\",\"phone\":\"13911110003\"," +
                        "\"password\":\"pass123\",\"roleCode\":\"booth_operator\"," +
                        "\"parkingLotIds\":[" + lotId + "]}"));

        String boothToken = login("13911110003", "pass123");

        // booth_operator 尝试创建停车场 → 403
        mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + boothToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权停车场\",\"totalSpaces\":100}"))
                .andExpect(status().isForbidden());
    }

    // ==================== ⑦ 平台用户跨租户 ====================

    @Test
    @DisplayName("平台用户可查看任意租户停车场")
    void shouldAllowPlatformUserToViewAnyParkingLot() throws Exception {
        customerAdminToken = login("13900000001", "pass123");
        Long lotId = createParkingLotViaApi(customerAdminToken, "平台可查停车场", 100);

        // 超级管理员（平台用户）可以查看任何停车场
        mockMvc.perform(get("/admin/parking-lots/" + lotId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("平台可查停车场"));
    }

    // ==================== 工具方法 ====================

    private Long registerAndApprove(String phone, String companyName, String contactPerson) throws Exception {
        // 注册
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"pass123\"}",
                                companyName, contactPerson, phone)))
                .andExpect(status().isOk());

        // 查找 tenant
        Tenant tenant = tenantMapper.selectByContactPhoneIgnoreTenant(phone);

        // 审核通过
        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVED\",\"reason\":\"测试通过\"}"));

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

    /**
     * 通过 API 创建停车场，返回 parkingLotId。
     */
    private Long createParkingLotViaApi(String token, Long companyId, String name, int totalSpaces) throws Exception {
        String resp = mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"totalSpaces\":%d,\"companyId\":%d}", name, totalSpaces, companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    /**
     * 兼容旧调用：使用当前租户默认公司创建停车场。
     */
    private Long createParkingLotViaApi(String token, String name, int totalSpaces) throws Exception {
        return createParkingLotViaApi(token, defaultCompanyId, name, totalSpaces);
    }

    /**
     * 直接通过 Mapper 插入停车场（跳过 Service 就绪检查，用于需要指定状态的测试）。
     * FIX-08：新停车场默认 DISABLED，需要 ENABLED 状态的测试用例使用此方法。
     */
    private Long createParkingLotInDb(String name, int totalSpaces, String status) {
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(tenantId);
        lot.setCompanyId(defaultCompanyId);
        lot.setGroupId(defaultCompanyId);
        lot.setName(name);
        lot.setAddress("");
        lot.setContactPhone("");
        lot.setTotalSpaces(totalSpaces);
        lot.setCurrentVehicles(0);
        lot.setRemainingSpaces(totalSpaces);
        lot.setStatus(status);
        lot.setPaymentMode("PLATFORM");
        lot.setImageRetentionDays(30);
        lot.setDataRetentionDays(365);
        lot.setFreeExitMinutes(15);
        lot.setManualReleasePolicy("ADMIN_ONLY");
        lot.setOfflinePolicy("ALLOW_ENTRY_EXIT");
        lot.setCreatedAt(java.time.LocalDateTime.now());
        lot.setUpdatedAt(java.time.LocalDateTime.now());
        parkingLotMapper.insert(lot);
        return lot.getId();
    }

    /**
     * 创建已就绪的停车场（含 MIXED 车道 + 相机 + 道闸），就绪检查可通过。
     * FIX-08：启用需要就绪检查，用于测试完整的 disable → re-enable 流程。
     * 返回 parkingLotId。
     */
    private Long setupReadyParkingLot(String name, int totalSpaces) {
        // 1. 创建 ENABLED 停车场（直接 DB 插入）
        Long lotId = createParkingLotInDb(name, totalSpaces, "ENABLED");

        // 2. 创建 MIXED 车道（同时满足入口和出口需求）
        ParkingLane lane = new ParkingLane();
        lane.setTenantId(tenantId);
        lane.setParkingLotId(lotId);
        lane.setName(name + "-1号车道");
        lane.setCode("LANE-01");
        lane.setDirection("MIXED");
        lane.setStatus("ENABLED");
        lane.setIsKeyLane(1);
        lane.setAutoReleasePolicy("MANUAL");
        lane.setDescription("");
        lane.setCreatedAt(java.time.LocalDateTime.now());
        lane.setUpdatedAt(java.time.LocalDateTime.now());
        laneMapper.insert(lane);

        // 3. 创建相机设备（vendorId=1 臻识, modelId=1 C5H）
        Device camera = new Device();
        camera.setTenantId(tenantId);
        camera.setParkingLotId(lotId);
        camera.setLaneId(lane.getId());
        camera.setVendorId(1L);
        camera.setModelId(1L);
        camera.setName(name + "-入口相机");
        camera.setCode("CAM-01");
        camera.setDeviceSn("TEST-SN-" + lotId + "-CAM");
        camera.setDeviceType("CAMERA");
        camera.setStatus("ENABLED");
        camera.setCapabilities("RECOGNIZE,CAPTURE");
        camera.setDescription("");
        camera.setCreatedAt(java.time.LocalDateTime.now());
        camera.setUpdatedAt(java.time.LocalDateTime.now());
        deviceMapper.insert(camera);

        // 4. 创建道闸设备（executorDeviceId 指向相机）
        Device gate = new Device();
        gate.setTenantId(tenantId);
        gate.setParkingLotId(lotId);
        gate.setLaneId(lane.getId());
        gate.setVendorId(1L);
        gate.setModelId(1L);
        gate.setName(name + "-入口道闸");
        gate.setCode("GATE-01");
        gate.setDeviceSn("TEST-SN-" + lotId + "-GATE");
        gate.setDeviceType("GATE");
        gate.setStatus("ENABLED");
        gate.setCapabilities("GATE_OPEN");
        gate.setDescription("");
        gate.setExecutorDeviceId(camera.getId());
        gate.setCreatedAt(java.time.LocalDateTime.now());
        gate.setUpdatedAt(java.time.LocalDateTime.now());
        deviceMapper.insert(gate);

        return lotId;
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private Long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        String idStr = json.substring(start, end).trim();
        return Long.parseLong(idStr);
    }

    private void cleanupTestData() {
        // 使用 JdbcTemplate 直接执行清理 SQL，绕过 MyBatis-Plus 租户拦截器
        // 1. 清理测试停车场关联数据
        String[] namePatterns = {"测试", "T18", "默认", "零车位", "负数", "列表",
                "详情", "他人", "原始", "部分", "可停", "可启", "双禁", "无原因", "管理员不可",
                "待启", "扩容", "修正", "负数容量", "无效字段", "缺原因", "权限测试", "平台可查",
                "跨查", "启用", "待停用"};
        for (String pattern : namePatterns) {
            String likePattern = "%" + pattern + "%";
            jdbcTemplate.update(
                    "DELETE d FROM device d JOIN parking_lot pl ON d.parking_lot_id = pl.id WHERE pl.name LIKE ?",
                    likePattern);
            jdbcTemplate.update(
                    "DELETE l FROM parking_lane l JOIN parking_lot pl ON l.parking_lot_id = pl.id WHERE pl.name LIKE ?",
                    likePattern);
            jdbcTemplate.update(
                    "DELETE c FROM parking_lot_capacity_log c JOIN parking_lot pl ON c.parking_lot_id = pl.id WHERE pl.name LIKE ?",
                    likePattern);
            jdbcTemplate.update(
                    "DELETE s FROM parking_lot_status_log s JOIN parking_lot pl ON s.parking_lot_id = pl.id WHERE pl.name LIKE ?",
                    likePattern);
            jdbcTemplate.update("DELETE FROM parking_lot WHERE name LIKE ?", likePattern);
        }

        // 2. 清理测试租户、默认公司及用户
        String[] phones = {"13900000001", "13900000002", "13900000003",
                "13911110001", "13911110002", "13911110003"};
        for (String phone : phones) {
            jdbcTemplate.update(
                    "DELETE c FROM company c JOIN tenant t ON c.tenant_id = t.id WHERE t.contact_phone = ?",
                    phone);
            jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", phone);
            jdbcTemplate.update("DELETE FROM tenant WHERE contact_phone = ?", phone);
        }
    }
}
