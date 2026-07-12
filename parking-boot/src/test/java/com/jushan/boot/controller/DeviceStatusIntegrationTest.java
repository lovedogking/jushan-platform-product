package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.DeviceStatusSnapshot;
import com.jushan.system.entity.DeviceVendor;
import com.jushan.system.entity.Tenant;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DeviceStatusSnapshotMapper;
import com.jushan.system.mapper.DeviceVendorMapper;
import com.jushan.system.mapper.ParkingLotMapper;
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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备状态查询、快照与轮询集成测试（T24）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "jushan.device-access.read-timeout=2000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("设备状态查询、快照与轮询集成测试")
class DeviceStatusIntegrationTest extends TestcontainersBaseTest {

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
    @Autowired private DeviceStatusSnapshotMapper snapshotMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private String parkingManagerToken;
    private Long parkingLotId;
    private Long vendorId;
    private Long modelId;
    private Long deviceId;
    private String deviceSn = "ZHENSHI_C5H_SN_T24";

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

        registerAndApprove("13920000001", "T24测试租户", "管理员");
        customerAdminToken = login("13920000001", "pass123");
        parkingLotId = createParkingLot(customerAdminToken, "T24测试停车场", 100);

        DeviceVendor vendor = vendorMapper.selectOne(
                new LambdaQueryWrapper<DeviceVendor>().eq(DeviceVendor::getCode, "ZHENSHI"));
        vendorId = vendor.getId();

        String modelsJson = mockMvc.perform(get("/admin/devices/models?vendorId=" + vendorId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        modelId = parseId(modelsJson);

        deviceId = createDevice("T24测试设备", "T24_DEV", "CAMERA", deviceSn);

        // FIX-12：创建无 device:manage 的 parking_manager 用于"无权限拒绝"测试
        createEmployee("T24运维", "13920000002", "parking_manager", parkingLotId);
        parkingManagerToken = login("13920000002", "pass123");
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ==================== ① 正常状态查询 ====================

    @Test
    @DisplayName("设备在线 → 返回 online=true")
    void shouldReturnOnlineStatus() throws Exception {
        // Use WireMock.stubFor explicitly to avoid ambiguity with MockMvcRequestBuilders
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_C5H_SN_T24",
                                        "online": true,
                                        "lastOnlineTime": "2026-07-11 10:00:00",
                                        "status": "connected"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.online").value(true))
                .andExpect(jsonPath("$.data.lastQuerySuccess").value(true))
                .andExpect(jsonPath("$.data.collectedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.snapshotAgeSeconds").isNumber())
                .andExpect(jsonPath("$.data.stale").value(false));

        DeviceStatusSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<DeviceStatusSnapshot>()
                        .eq(DeviceStatusSnapshot::getDeviceId, deviceId)
                        .orderByDesc(DeviceStatusSnapshot::getCollectedAt)
                        .last("LIMIT 1"));
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getQuerySuccess()).isTrue();
        assertThat(snapshot.getOnline()).isTrue();
    }

    @Test
    @DisplayName("道闸设备返回 gateStatus → 正确填充")
    void shouldReturnGateStatus() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_C5H_SN_T24",
                                        "online": true,
                                        "lastOnlineTime": "2026-07-11 10:00:00",
                                        "status": "connected",
                                        "gateStatus": "closed",
                                        "gateConnectStatus": "connected"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gateStatus").value("closed"))
                .andExpect(jsonPath("$.data.gateConnectStatus").value("connected"));
    }

    @Test
    @DisplayName("设备离线 → 返回 online=false")
    void shouldReturnOfflineStatus() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_C5H_SN_T24",
                                        "online": false,
                                        "lastOnlineTime": "2026-07-10 08:00:00",
                                        "status": "disconnected"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(false))
                .andExpect(jsonPath("$.data.statusDescription").value("disconnected"));
    }

    // ==================== ② 错误场景 ====================

    @Test
    @DisplayName("DA 返回 404 → 快照记录失败，错误码 404")
    void shouldRecord404Error() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 404,
                                    "message": "device not found"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastQuerySuccess").value(false))
                .andExpect(jsonPath("$.data.lastErrorCode").value("404"));

        DeviceStatusSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<DeviceStatusSnapshot>()
                        .eq(DeviceStatusSnapshot::getDeviceId, deviceId)
                        .orderByDesc(DeviceStatusSnapshot::getCollectedAt)
                        .last("LIMIT 1"));
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getQuerySuccess()).isFalse();
        assertThat(snapshot.getErrorCode()).isEqualTo("404");
    }

    @Test
    @DisplayName("DA 返回 503 → 快照记录失败，错误码 503")
    void shouldRecord503Error() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 503,
                                    "message": "MQTT connection unavailable"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastQuerySuccess").value(false))
                .andExpect(jsonPath("$.data.lastErrorCode").value("503"));
    }

    @Test
    @DisplayName("网络超时 → 快照记录失败，错误码 UNCERTAIN")
    void shouldRecordUncertainOnTimeout() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5000)
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {"deviceSn": "ZHENSHI_C5H_SN_T24", "online": true}
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastQuerySuccess").value(false))
                .andExpect(jsonPath("$.data.lastErrorCode").value("UNCERTAIN"));
    }

    // ==================== ③ 快照读取 ====================

    @Test
    @DisplayName("获取最新快照（从未查询过）→ 所有状态字段为 null")
    void shouldReturnEmptySnapshotWhenNeverQueried() throws Exception {
        mockMvc.perform(get("/admin/devices/" + deviceId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deviceId").value(deviceId))
                .andExpect(jsonPath("$.data.online").doesNotExist());
    }

    @Test
    @DisplayName("获取最新快照（已查询过）→ 返回持久化数据")
    void shouldReturnLatestSnapshot() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_C5H_SN_T24",
                                        "online": true,
                                        "lastOnlineTime": "2026-07-11 10:00:00",
                                        "status": "connected"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                .header("Authorization", "Bearer " + adminToken));

        mockMvc.perform(get("/admin/devices/" + deviceId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true))
                .andExpect(jsonPath("$.data.snapshotAgeSeconds").isNumber())
                .andExpect(jsonPath("$.data.lastQuerySuccess").value(true));
    }

    // ==================== ④ 批量查询 ====================

    @Test
    @DisplayName("批量查询多台设备 → 各设备独立返回")
    void shouldBatchQueryMultipleDevices() throws Exception {
        Long deviceId2 = createDevice("T24测试设备2", "T24_DEV2", "CAMERA", "ZHENSHI_SN_T24_2");

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_C5H_SN_T24",
                                        "online": true,
                                        "lastOnlineTime": "2026-07-11 10:00:00",
                                        "status": "connected"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/ZHENSHI_SN_T24_2/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "ZHENSHI_SN_T24_2",
                                        "online": false,
                                        "lastOnlineTime": "2026-07-10 08:00:00",
                                        "status": "disconnected"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/query-status-batch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIds\":[" + deviceId + "," + deviceId2 + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("批量查询缺少 deviceIds → 返回 code=400")
    void shouldRejectBatchQueryWithoutDeviceIds() throws Exception {
        mockMvc.perform(post("/admin/devices/query-status-batch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== ⑤ 安全与权限 ====================

    @Test
    @DisplayName("跨租户读取设备状态快照 → 403")
    void shouldRejectCrossTenantStatusQuery() throws Exception {
        registerAndApprove("13920000009", "T24跨租户", "管理员");
        String otherToken = login("13920000009", "pass123");
        Long otherLotId = createParkingLot(otherToken, "其他租户停车场", 200);
        Long otherDeviceId = createDeviceViaApiWithLot(otherLotId, "其他设备", "OTHER", "CAMERA", "OTHER_SN");

        // 跨租户读取快照（使用 device:read 权限的 token 访问其他租户设备）
        mockMvc.perform(get("/admin/devices/" + otherDeviceId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    @Test
    @DisplayName("无 device:manage 权限 → 403（FIX-12：使用 parking_manager，其仅有 device:read）")
    void shouldRejectNoPermissionForStatusQuery() throws Exception {
        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + parkingManagerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("device:read 可读取快照但不可触发实时查询（FIX-12：使用 parking_manager）")
    void shouldAllowSnapshotReadButNotRealTimeQuery() throws Exception {
        mockMvc.perform(get("/admin/devices/" + deviceId + "/status")
                        .header("Authorization", "Bearer " + parkingManagerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + parkingManagerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登录访问状态接口 → 401")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/devices/" + deviceId + "/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("已停用设备查询状态 → 拒绝")
    void shouldRejectQueryDisabledDevice() throws Exception {
        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已停用")));
    }

    // ==================== ⑥ 批量快照读取 ====================

    @Test
    @DisplayName("批量获取最新快照 → 返回各设备已有/空快照")
    void shouldBatchGetSnapshots() throws Exception {
        Long deviceId2 = createDevice("T24设备B", "T24_B", "CAMERA", "ZHENSHI_SN_T24_B");

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/devices/" + deviceSn + "/status"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {"deviceSn": "ZHENSHI_C5H_SN_T24", "online": true},
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/query-status")
                .header("Authorization", "Bearer " + adminToken));

        mockMvc.perform(post("/admin/devices/status-snapshots")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIds\":[" + deviceId + "," + deviceId2 + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2));
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
        snapshotMapper.delete(new LambdaQueryWrapper<>());
        deviceMapper.delete(new LambdaQueryWrapper<>());
        parkingLotMapper.delete(new LambdaQueryWrapper<>());
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().ne(SysUser::getUsername, "admin"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>().isNotNull(Tenant::getContactPhone));
    }
}
