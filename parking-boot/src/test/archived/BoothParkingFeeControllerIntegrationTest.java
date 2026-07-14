package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.BillingRule;
import com.jushan.system.entity.BillingRuleVersion;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.BillingRuleMapper;
import com.jushan.system.mapper.BillingRuleVersionMapper;
import com.jushan.system.mapper.EmployeeParkingLotMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 岗亭停车费用查询集成测试（P007）。
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
@DisplayName("岗亭停车费用查询集成测试")
class BoothParkingFeeControllerIntegrationTest extends TestcontainersBaseTest {

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
    private EmployeeParkingLotMapper employeeParkingLotMapper;
    @Autowired
    private ParkingLotMapper parkingLotMapper;
    @Autowired
    private BillingRuleMapper billingRuleMapper;
    @Autowired
    private BillingRuleVersionMapper billingRuleVersionMapper;
    @Autowired
    private ParkingRecordMapper parkingRecordMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String BOOTH_PHONE = "13977770101";

    private String adminToken;
    private String customerAdminToken;
    private String boothOperatorToken;
    private Long parkingLotAId;
    private Long parkingLotBId;
    private Long recordAId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        cleanupTestData();

        registerAndApprove("13977770001", "岗亭费用测试租户", "测试联系人");
        customerAdminToken = login("13977770001", "pass123");

        parkingLotAId = createParkingLotViaApi(customerAdminToken, "岗亭费用停车场A", 100);
        parkingLotBId = createParkingLotViaApi(customerAdminToken, "岗亭费用停车场B", 200);

        createParkingLotAndRule(parkingLotAId, 30, 500);
        createParkingLotAndRule(parkingLotBId, 30, 500);

        recordAId = createParkingRecord(parkingLotAId, "京A12345", ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(90));

        // booth_operator 仅授权停车场 A
        createEmployee(customerAdminToken, "岗亭操作员", BOOTH_PHONE, "booth_operator", parkingLotAId);
        boothOperatorToken = login(BOOTH_PHONE, "pass123");
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @DisplayName("booth_operator 查询授权停车场费用成功")
    void boothOperatorCanQueryAuthorizedLot() throws Exception {
        mockMvc.perform(get("/booth/parking/fee?plate=京A12345&parkingLotId=" + parkingLotAId)
                        .header("Authorization", "Bearer " + boothOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordAId))
                .andExpect(jsonPath("$.data.feeCents").value(500))
                .andExpect(jsonPath("$.data.payable").value(true));
    }

    @Test
    @DisplayName("booth_operator 查询非授权停车场 -> 403")
    void boothOperatorCannotQueryUnauthorizedLot() throws Exception {
        // 先在 B 创建一条记录
        createParkingRecord(parkingLotBId, "京B99999", ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(30));

        mockMvc.perform(get("/booth/parking/fee?plate=京B99999&parkingLotId=" + parkingLotBId)
                        .header("Authorization", "Bearer " + boothOperatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无 record:read 权限角色访问岗亭接口 -> 403")
    void roleWithoutRecordReadCannotAccess() throws Exception {
        // 创建仅 finance 角色员工（无 record:read）
        createEmployee(customerAdminToken, "财务人员", "13977770103", "finance", parkingLotAId);
        String financeToken = login("13977770103", "pass123");

        mockMvc.perform(get("/booth/parking/fee?plate=京A12345&parkingLotId=" + parkingLotAId)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("按记录 ID 查询授权停车场成功")
    void queryByRecordIdAuthorized() throws Exception {
        mockMvc.perform(get("/booth/parking/fee/" + recordAId)
                        .header("Authorization", "Bearer " + boothOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordAId))
                .andExpect(jsonPath("$.data.feeCents").value(500));
    }

    @Test
    @DisplayName("结算预览授权停车场成功")
    void previewAuthorized() throws Exception {
        mockMvc.perform(post("/booth/parking/fee/preview")
                        .header("Authorization", "Bearer " + boothOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordId\":" + recordAId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordAId))
                .andExpect(jsonPath("$.data.previewExitTime").exists());
    }

    // ==================== 辅助方法 ====================

    private String login(String username, String password) throws Exception {
        String resp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractToken(resp);
    }

    private Long registerAndApprove(String phone, String companyName, String contactPerson) throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"pass123\"}",
                                companyName, contactPerson, phone)))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, phone));

        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVED\",\"reason\":\"岗亭费用测试通过\"}"));

        return tenant.getId();
    }

    private Long createParkingLotViaApi(String token, String name, int totalSpaces) throws Exception {
        String resp = mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"totalSpaces\":%d}", name, totalSpaces)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private void createEmployee(String adminToken, String displayName, String phone,
                                 String roleCode, Long... parkingLotIds) throws Exception {
        StringBuilder lotIdsJson = new StringBuilder("[");
        for (int i = 0; i < parkingLotIds.length; i++) {
            if (i > 0) lotIdsJson.append(",");
            lotIdsJson.append(parkingLotIds[i]);
        }
        lotIdsJson.append("]");

        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"displayName\":\"%s\",\"phone\":\"%s\",\"password\":\"pass123\",\"roleCode\":\"%s\",\"parkingLotIds\":%s}",
                                displayName, phone, roleCode, lotIdsJson)))
                .andExpect(status().isOk());
    }

    private void createParkingLotAndRule(Long parkingLotId, int freeMinutes, int firstAmount) {
        BillingRule rule = new BillingRule();
        rule.setTenantId(1L);
        rule.setParkingLotId(parkingLotId);
        rule.setName("岗亭测试规则");
        rule.setRuleType(BillingRule.RULE_TYPE_FIXED);
        rule.setStatus(BillingRule.STATUS_ENABLED);
        billingRuleMapper.insert(rule);

        BillingRuleVersion version = new BillingRuleVersion();
        version.setRuleId(rule.getId());
        version.setTenantId(1L);
        version.setParkingLotId(parkingLotId);
        version.setVersion(1);
        version.setIsActive(1);
        version.setFreeMinutes(freeMinutes);
        version.setFirstAmount(firstAmount);
        billingRuleVersionMapper.insert(version);
    }

    private Long createParkingRecord(Long parkingLotId, String plate, String status,
                                      LocalDateTime entryTime) {
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(1L);
        record.setParkingLotId(parkingLotId);
        record.setStandardizedPlate(plate);
        record.setStatus(status);
        record.setEntryTime(entryTime);
        parkingRecordMapper.insert(record);
        return record.getId();
    }

    private void cleanupTestData() {
        parkingRecordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        billingRuleVersionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        billingRuleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingLotMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        employeeParkingLotMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        sysUserMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .like(SysUser::getUsername, "1397777"));
        tenantMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                .like(Tenant::getName, "岗亭费用测试租户"));
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
        return Long.parseLong(json.substring(start, end).trim());
    }
}
