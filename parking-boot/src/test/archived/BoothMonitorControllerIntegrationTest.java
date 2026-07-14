package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.*;
import com.jushan.system.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 岗亭监控 API 集成测试（P005）。
 * <p>
 * 验证：
 * <ul>
 *   <li>授权停车场可获取监控快照</li>
 *   <li>未授权停车场返回 403（fail-closed）</li>
 *   <li>跨租户访问被拒绝</li>
 *   <li>无权限角色（如设备运维）返回 403</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("P005 岗亭监控 API 集成测试")
class BoothMonitorControllerIntegrationTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantMapper tenantMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;
    @Autowired private EmployeeParkingLotMapper employeeParkingLotMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private String boothOperatorToken;
    private String deviceMaintenanceToken;

    private Long parkingLotAId;
    private Long parkingLotBId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        cleanupTestData();

        registerAndApprove("13988880001", "BoothMonitor租户", "BoothMonitor联系人");
        customerAdminToken = login("13988880001", "pass123");

        parkingLotAId = createParkingLotViaApi(customerAdminToken, "BoothMonitor停车场A", 100);
        parkingLotBId = createParkingLotViaApi(customerAdminToken, "BoothMonitor停车场B", 200);

        // 岗亭操作员仅授权停车场 A
        createEmployee(customerAdminToken, "BoothMonitor岗亭", "13988880101",
                "booth_operator", parkingLotAId);
        boothOperatorToken = login("13988880101", "pass123");

        // 设备运维无 record:read，用于权限拒绝测试
        createEmployee(customerAdminToken, "BoothMonitor运维", "13988880102",
                "device_maintenance", parkingLotAId);
        deviceMaintenanceToken = login("13988880102", "pass123");
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @DisplayName("岗亭操作员可获取授权停车场快照")
    void boothOperatorCanGetAuthorizedSnapshot() throws Exception {
        mockMvc.perform(get("/booth/monitor/snapshot?parkingLotId=" + parkingLotAId)
                        .header("Authorization", "Bearer " + boothOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.parkingLot.id").value(parkingLotAId))
                .andExpect(jsonPath("$.data.parkingLot.name", containsString("BoothMonitor停车场A")))
                .andExpect(jsonPath("$.data.recentEvents").exists())
                .andExpect(jsonPath("$.data.deviceStatuses").exists())
                .andExpect(jsonPath("$.data.alerts").exists());
    }

    @Test
    @DisplayName("岗亭操作员访问非授权停车场快照 → 403")
    void boothOperatorCannotGetUnauthorizedSnapshot() throws Exception {
        mockMvc.perform(get("/booth/monitor/snapshot?parkingLotId=" + parkingLotBId)
                        .header("Authorization", "Bearer " + boothOperatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("设备运维无 record:read，访问快照 → 403")
    void deviceMaintenanceCannotAccessSnapshot() throws Exception {
        mockMvc.perform(get("/booth/monitor/snapshot?parkingLotId=" + parkingLotAId)
                        .header("Authorization", "Bearer " + deviceMaintenanceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("跨租户访问停车场快照 → 403")
    void crossTenantSnapshotRejected() throws Exception {
        registerAndApprove("13988880002", "BoothMonitor租户2", "BoothMonitor联系人2");
        String cust2Token = login("13988880002", "pass123");
        Long lotInTenant2 = createParkingLotViaApi(cust2Token, "BoothMonitor租户2停车场", 100);

        mockMvc.perform(get("/booth/monitor/snapshot?parkingLotId=" + lotInTenant2)
                        .header("Authorization", "Bearer " + boothOperatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("平台管理员访问快照 → 403（岗亭接口禁止平台用户）")
    void platformAdminCannotAccessBoothSnapshot() throws Exception {
        mockMvc.perform(get("/booth/monitor/snapshot?parkingLotId=" + parkingLotAId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("缺少 parkingLotId → 400")
    void missingParkingLotIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/booth/monitor/snapshot")
                        .header("Authorization", "Bearer " + boothOperatorToken))
                .andExpect(status().isBadRequest());
    }

    // ==================== 工具方法 ====================

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
                        .content("{\"action\":\"APPROVED\",\"reason\":\"P005集成测试通过\"}"));

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

    private void cleanupTestData() {
        String[] phones = {"13988880001", "13988880002", "13988880101", "13988880102"};
        for (String phone : phones) {
            sysUserMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, phone));
            tenantMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                    .eq(Tenant::getContactPhone, phone));
        }
        for (String pattern : new String[]{"BoothMonitor"}) {
            java.util.List<ParkingLot> lots = parkingLotMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingLot>()
                            .like(ParkingLot::getName, pattern));
            for (ParkingLot lot : lots) {
                parkingLotMapper.deleteById(lot.getId());
            }
        }
    }
}
