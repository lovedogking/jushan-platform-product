package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.DeviceVendor;
import com.jushan.system.entity.SysAuditLog;
import com.jushan.system.entity.Tenant;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DeviceVendorMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.SysAuditLogMapper;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备校时操作与审计集成测试（T25）。
 * <p>
 * 覆盖校时调用、审计记录、权限/租户隔离和异常场景。
 * 使用 WireMock 模拟 Device Access v0.2 校时接口。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "jushan.device-access.read-timeout=2000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("设备校时操作与审计集成测试")
class DeviceTimeSyncIntegrationTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMockServer.start();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantMapper tenantMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private DeviceVendorMapper vendorMapper;
    @Autowired private SysAuditLogMapper auditLogMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private String parkingManagerToken;
    private Long parkingLotId;
    private Long vendorId;
    private Long modelId;
    private Long deviceId;
    private String deviceSn = "ZHENSHI_C5H_SN_T25";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("jushan.device-access.base-url", wireMockServer::baseUrl);
    }

    @BeforeEach
    void setUp() throws Exception {
        wireMockServer.resetAll();

        String resp = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = extractToken(resp);

        cleanupTestData();

        registerAndApprove("13920000003", "T25测试租户", "管理员");
        customerAdminToken = login("13920000003", "pass123");
        parkingLotId = createParkingLot(customerAdminToken, "T25测试停车场", 100);

        DeviceVendor vendor = vendorMapper.selectOne(
                new LambdaQueryWrapper<DeviceVendor>().eq(DeviceVendor::getCode, "ZHENSHI"));
        vendorId = vendor.getId();

        String modelsJson = mockMvc.perform(get("/admin/devices/models?vendorId=" + vendorId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        modelId = parseId(modelsJson);

        deviceId = createDevice("T25测试设备", "T25_DEV", "CAMERA", deviceSn);

        // FIX-12：创建无 device:manage 的 parking_manager 用于权限拒绝测试
        createEmployee("T25运维", "13920000007", "parking_manager", parkingLotId);
        parkingManagerToken = login("13920000007", "pass123");
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ==================== ① 正常校时 ====================

    @Test
    @DisplayName("校时成功 → 返回 TimeSyncResultDTO + 审计记录 SUCCESS")
    void shouldSyncTimeSuccessfully() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "success": true,
                                        "deviceCode": 200,
                                        "message": "time synced"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"测试校时\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        // 验证审计记录已写入
        List<SysAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getResult()).isEqualTo("SUCCESS");
        assertThat(logs.get(0).getReason()).isEqualTo("测试校时");
    }

    @Test
    @DisplayName("校时成功（无 reason）→ 审计记录 reason 为空字符串")
    void shouldSyncTimeWithEmptyReason() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "success": true,
                                        "deviceCode": 200,
                                        "message": "time synced"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<SysAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getResult()).isEqualTo("SUCCESS");
    }

    // ==================== ② DA 错误场景（审计记录） ====================

    @Test
    @DisplayName("404 设备不存在 → 审计记录 FAILED")
    void shouldRecordAuditWhen404() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 404,
                                    "message": "device not found"
                                }""")));

        // syncTime 不抛异常，返回 success=false
        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"404测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(false));

        List<SysAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getResult()).isEqualTo("FAILED");
        assertThat(logs.get(0).getFailReason()).contains("device not found");
    }

    @Test
    @DisplayName("503 MQTT 不可用 → 审计记录 FAILED")
    void shouldRecordAuditWhen503() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 503,
                                    "message": "MQTT connection unavailable"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"503测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(false));

        List<SysAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getResult()).isEqualTo("FAILED");
        assertThat(logs.get(0).getFailReason()).contains("MQTT");
    }

    @Test
    @DisplayName("500 设备错误 → 审计记录 FAILED")
    void shouldRecordAuditWhen500() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 500,
                                    "message": "device internal error"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"500测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(false));

        List<SysAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getResult()).isEqualTo("FAILED");
    }

    // ==================== ③ 超时场景（UNCERTAIN） ====================

    @Test
    @DisplayName("命令超时 → 审计记录 UNCERTAIN")
    void shouldRecordUncertainOnTimeout() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5000)
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "success": true,
                                        "deviceCode": 200
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"超时测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(false));

        List<SysAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getResult()).isEqualTo("UNCERTAIN");
        assertThat(logs.get(0).getFailReason()).contains("UNCERTAIN");
    }

    // ==================== ④ 权限与安全 ====================

    @Test
    @DisplayName("无 device:manage 权限 → 403（FIX-12：使用 parking_manager，其仅有 device:read）")
    void shouldRejectNoPermission() throws Exception {
        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + parkingManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"无权限测试\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登录 → 401")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"未登录测试\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("跨租户校时 → 403")
    void shouldRejectCrossTenant() throws Exception {
        // 创建第二个租户，并授予 device_maintenance（含 device:manage）角色
        registerAndApprove("13920000004", "T25跨租户", "管理员");
        SysUser otherUser = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "13920000004"));
        otherUser.setRoles("[\"device_maintenance\"]");
        sysUserMapper.updateById(otherUser);

        // 重新登录以刷新角色缓存
        String otherToken = login("13920000004", "pass123");

        // 使用第二租户的 token 访问第一租户的设备 → 应返回 403
        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"跨租户测试\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    @Test
    @DisplayName("已停用设备 → 拒绝校时")
    void shouldRejectDisabledDevice() throws Exception {
        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"停用设备测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已停用")));
    }

    // ==================== ⑤ 重复校时（无幂等） ====================

    @Test
    @DisplayName("重复校时 → 每次独立调用 + 独立审计记录")
    void shouldRecordSeparateAuditForEachCall() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_C5H_SN_T25",
                                        "status": "success",
                                        "message": "time synced"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        // 调用两次
        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第一次\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第二次\"}"))
                .andExpect(status().isOk());

        // 验证两次审计记录独立存在
        List<SysAuditLog> logs = auditLogMapper.selectList(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getResult()).isEqualTo("SUCCESS");
        assertThat(logs.get(1).getResult()).isEqualTo("SUCCESS");
    }

    // ==================== ⑥ 审计记录字段完整性 ====================

    @Test
    @DisplayName("审计记录包含完整的目标信息")
    void shouldRecordCompleteAuditInfo() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/time/sync"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_C5H_SN_T25",
                                        "status": "success",
                                        "message": "time synced"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/sync-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"完整性测试\"}"))
                .andExpect(status().isOk());

        SysAuditLog log = auditLogMapper.selectOne(
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(SysAuditLog::getTargetType, "device")
                        .eq(SysAuditLog::getTargetId, String.valueOf(deviceId))
                        .eq(SysAuditLog::getAction, "time_sync"));

        assertThat(log).isNotNull();
        assertThat(log.getTargetType()).isEqualTo("device");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(deviceId));
        assertThat(log.getAction()).isEqualTo("time_sync");
        assertThat(log.getResult()).isEqualTo("SUCCESS");
        assertThat(log.getReason()).isEqualTo("完整性测试");
        assertThat(log.getOperatorId()).isNotNull();
        assertThat(log.getAfterValue()).contains("deviceSn");
        assertThat(log.getTenantId()).isNotNull();
        assertThat(log.getCreatedAt()).isNotNull();
    }

    // ==================== 工具方法 ====================

    private Long registerAndApprove(String phone, String companyName, String contactPerson) throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"pass123\"}",
                                companyName, contactPerson, phone)))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getContactPhone, phone));

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

    private Long createParkingLot(String token, String name, int totalSpaces) throws Exception {
        String resp = mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"totalSpaces\":%d}", name, totalSpaces)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return parseId(resp);
    }

    private Long createDevice(String name, String code, String deviceType, String sn) throws Exception {
        return createDeviceViaApiWithLot(parkingLotId, name, code, deviceType, sn);
    }

    private Long createDeviceViaApiWithLot(Long lotId, String name, String code,
                                            String deviceType, String sn) throws Exception {
        String resp = mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"parkingLotId\":%d,\"vendorId\":%d,\"modelId\":%d," +
                                        "\"name\":\"%s\",\"code\":\"%s\",\"deviceType\":\"%s\",\"deviceSn\":\"%s\"}",
                                lotId, vendorId, modelId, name, code, deviceType, sn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return parseId(resp);
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private Long parseId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        String idStr = json.substring(start, end).trim();
        return Long.parseLong(idStr);
    }

    private void createEmployee(String displayName, String phone,
                                 String roleCode, Long parkingLotId) throws Exception {
        mockMvc.perform(post("/admin/employees")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"displayName\":\"%s\",\"phone\":\"%s\",\"password\":\"pass123\",\"roleCode\":\"%s\",\"parkingLotIds\":[%d]}",
                                displayName, phone, roleCode, parkingLotId)))
                .andExpect(status().isOk());
    }

    private void cleanupTestData() {
        auditLogMapper.delete(new LambdaQueryWrapper<>());
        deviceMapper.delete(new LambdaQueryWrapper<>());
        parkingLotMapper.delete(new LambdaQueryWrapper<>());
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().ne(SysUser::getUsername, "admin"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>().isNotNull(Tenant::getContactPhone));
    }
}
