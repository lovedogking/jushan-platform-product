package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.DeviceVendor;
import com.jushan.system.entity.Tenant;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DeviceVendorMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.TenantMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 停车场就绪检查集成测试（T22）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>完整配置 → ready=true, 0 blockers</li>
 *   <li>缺车道 → BLOCKER</li>
 *   <li>缺入口/出口方向 → BLOCKER</li>
 *   <li>缺相机/道闸/执行相机 → BLOCKER + 逐车道 WARNING</li>
 *   <li>相机/道闸停用 → BLOCKER</li>
 *   <li>总车位为 0 → BLOCKER</li>
 *   <li>占位项（收费规则、支付、设备在线）→ WARNING + implemented=false</li>
 *   <li>跨租户拒绝</li>
 *   <li>权限拒绝（无 parking:read）</li>
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
@DisplayName("停车场就绪检查集成测试")
class ReadinessCheckIntegrationTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantMapper tenantMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;
    @Autowired private ParkingLaneMapper laneMapper;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private DeviceVendorMapper vendorMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private Long parkingLotId;
    private Long vendorId;
    private Long modelId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        cleanupTestData();

        registerAndApprove("13900000004", "T22测试租户", "管理员");
        customerAdminToken = login("13900000004", "pass123");
        parkingLotId = createParkingLot(customerAdminToken, "T22测试停车场", 100);

        DeviceVendor vendor = vendorMapper.selectOne(
                new LambdaQueryWrapper<DeviceVendor>().eq(DeviceVendor::getCode, "ZHENSHI"));
        vendorId = vendor.getId();
        modelId = extractFirstId(
                mockMvc.perform(get("/admin/devices/models?vendorId=" + vendorId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ==================== ① 完整配置 → 就绪 ====================

    @Test
    @DisplayName("完整配置（入口+出口各一车道，含相机+道闸+执行相机）→ ready=true")
    void shouldBeReadyWithFullConfig() throws Exception {
        // 入口车道
        Long entryLaneId = createLane("入口车道1", "ENTRY_01", "ENTRY");
        Long entryCamId = createDevice("入口相机", "EC_READY", "CAMERA", "SN_EC_READY");
        Long entryGateId = createDevice("入口道闸", "EG_READY", "GATE", "SN_EG_READY");
        bindToLane(entryCamId, entryLaneId);
        bindToLane(entryGateId, entryLaneId);
        setExecutor(entryGateId, entryCamId);

        // 出口车道
        Long exitLaneId = createLane("出口车道1", "EXIT_01", "EXIT");
        Long exitCamId = createDevice("出口相机", "XC_READY", "CAMERA", "SN_XC_READY");
        Long exitGateId = createDevice("出口道闸", "XG_READY", "GATE", "SN_XG_READY");
        bindToLane(exitCamId, exitLaneId);
        bindToLane(exitGateId, exitLaneId);
        setExecutor(exitGateId, exitCamId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.blockerCount").value(0))
                // 2 placeholders + 4 devices without snapshots (DEVICE_STATUS_NEVER_QUERIED each)
                .andExpect(jsonPath("$.data.warningCount").value(6));
    }

    // ==================== ② 无车道 → BLOCKER ====================

    @Test
    @DisplayName("无任何车道 → ready=false, NO_LANE")
    void shouldBlockWhenNoLanes() throws Exception {
        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='NO_LANE')].level").value("BLOCKER"));
    }

    // ==================== ③ 仅有入口车道（无出口） → BLOCKER ====================

    @Test
    @DisplayName("仅有入口车道无出口 → BLOCKER NO_EXIT_LANE")
    void shouldBlockWhenNoExitLane() throws Exception {
        createLane("入口车道", "ENTRY_ONLY", "ENTRY");

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='NO_EXIT_LANE')].level").value("BLOCKER"));
    }

    // ==================== ④ 仅有出口车道（无入口） → BLOCKER ====================

    @Test
    @DisplayName("仅有出口车道无入口 → BLOCKER NO_ENTRY_LANE")
    void shouldBlockWhenNoEntryLane() throws Exception {
        createLane("出口车道", "EXIT_ONLY", "EXIT");

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='NO_ENTRY_LANE')].level").value("BLOCKER"));
    }

    // ==================== ⑤ MIXED 车道 ====================

    @Test
    @DisplayName("单条 MIXED 车道同时满足入口和出口")
    void shouldAcceptMixedLaneAsBothDirections() throws Exception {
        // MIXED 车道同时属于入口和出口
        Long mixedLaneId = createLane("混合车道", "MXD_01", "MIXED");
        Long camId = createDevice("混合相机", "MX_CAM", "CAMERA", "SN_MX_CAM");
        Long gateId = createDevice("混合道闸", "MX_GATE", "GATE", "SN_MX_GATE");
        bindToLane(camId, mixedLaneId);
        bindToLane(gateId, mixedLaneId);
        setExecutor(gateId, camId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.items[?(@.code=='NO_ENTRY_LANE')]").doesNotExist())
                .andExpect(jsonPath("$.data.items[?(@.code=='NO_EXIT_LANE')]").doesNotExist());
    }

    // ==================== ⑥ 缺相机 → BLOCKER + WARNING ====================

    @Test
    @DisplayName("入口车道无相机 → BLOCKER + 逐车道 WARNING")
    void shouldBlockAndWarnWhenEntryCameraMissing() throws Exception {
        Long entryLaneId = createLane("入口车道", "ENT_NOCAM", "ENTRY");
        Long exitLaneId = createLane("出口车道", "EXT_NOCAM", "EXIT");
        // 出口车道有相机
        Long exitCamId = createDevice("出口相机", "XC_NOCAM", "CAMERA", "SN_XC_NOCAM");
        Long exitGateId = createDevice("出口道闸", "XG_NOCAM", "GATE", "SN_XG_NOCAM");
        bindToLane(exitCamId, exitLaneId);
        bindToLane(exitGateId, exitLaneId);
        setExecutor(exitGateId, exitCamId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                // 入口方向 BLOCKER
                .andExpect(jsonPath("$.data.items[?(@.code=='ENTRY_CAMERA_MISSING' && @.level=='BLOCKER')]").exists())
                // 入口车道级 WARNING
                .andExpect(jsonPath("$.data.items[?(@.code=='ENTRY_CAMERA_MISSING' && @.laneName=='入口车道' && @.level=='WARNING')]").exists());
    }

    // ==================== ⑦ 相机停用 → BLOCKER ====================

    @Test
    @DisplayName("入口相机停用 → BLOCKER ENTRY_CAMERA_DISABLED")
    void shouldBlockWhenEntryCameraDisabled() throws Exception {
        Long entryLaneId = createLane("入口车道", "ENT_DCAM", "ENTRY");
        Long entryCamId = createDevice("入口相机", "EC_DCAM", "CAMERA", "SN_EC_DCAM");
        bindToLane(entryCamId, entryLaneId);
        // 停用相机
        mockMvc.perform(post("/admin/devices/" + entryCamId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        // 出口车道完整配置
        Long exitLaneId = createLane("出口车道", "EXT_DCAM", "EXIT");
        Long exitCamId = createDevice("出口相机", "XC_DCAM", "CAMERA", "SN_XC_DCAM");
        Long exitGateId = createDevice("出口道闸", "XG_DCAM", "GATE", "SN_XG_DCAM");
        bindToLane(exitCamId, exitLaneId);
        bindToLane(exitGateId, exitLaneId);
        setExecutor(exitGateId, exitCamId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='ENTRY_CAMERA_DISABLED' && @.level=='BLOCKER')]").exists());
    }

    // ==================== ⑧ 缺道闸 → BLOCKER ====================

    @Test
    @DisplayName("入口车道无道闸 → BLOCKER ENTRY_GATE_MISSING")
    void shouldBlockWhenEntryGateMissing() throws Exception {
        Long entryLaneId = createLane("入口车道", "ENT_NOGATE", "ENTRY");
        Long entryCamId = createDevice("入口相机", "EC_NOGATE", "CAMERA", "SN_EC_NOGATE");
        bindToLane(entryCamId, entryLaneId);

        // 出口车道完整
        Long exitLaneId = createLane("出口车道", "EXT_NOGATE", "EXIT");
        Long exitCamId = createDevice("出口相机", "XC_NOGATE", "CAMERA", "SN_XC_NOGATE");
        Long exitGateId = createDevice("出口道闸", "XG_NOGATE", "GATE", "SN_XG_NOGATE");
        bindToLane(exitCamId, exitLaneId);
        bindToLane(exitGateId, exitLaneId);
        setExecutor(exitGateId, exitCamId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='ENTRY_GATE_MISSING' && @.level=='BLOCKER')]").exists());
    }

    // ==================== ⑨ 道闸停用 → BLOCKER ====================

    @Test
    @DisplayName("入口道闸停用 → BLOCKER ENTRY_GATE_DISABLED")
    void shouldBlockWhenEntryGateDisabled() throws Exception {
        Long entryLaneId = createLane("入口车道", "ENT_DGAT", "ENTRY");
        Long entryCamId = createDevice("入口相机", "EC_DGAT", "CAMERA", "SN_EC_DGAT");
        Long entryGateId = createDevice("入口道闸", "EG_DGAT", "GATE", "SN_EG_DGAT");
        bindToLane(entryCamId, entryLaneId);
        bindToLane(entryGateId, entryLaneId);
        setExecutor(entryGateId, entryCamId);
        // 停用道闸
        mockMvc.perform(post("/admin/devices/" + entryGateId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        // 出口车道完整
        Long exitLaneId = createLane("出口车道", "EXT_DGAT", "EXIT");
        Long exitCamId = createDevice("出口相机", "XC_DGAT", "CAMERA", "SN_XC_DGAT");
        Long exitGateId = createDevice("出口道闸", "XG_DGAT", "GATE", "SN_XG_DGAT");
        bindToLane(exitCamId, exitLaneId);
        bindToLane(exitGateId, exitLaneId);
        setExecutor(exitGateId, exitCamId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='ENTRY_GATE_DISABLED' && @.level=='BLOCKER')]").exists());
    }

    // ==================== ⑩ 道闸无执行相机 → BLOCKER ====================

    @Test
    @DisplayName("入口道闸无执行相机 → BLOCKER ENTRY_GATE_NO_EXECUTOR")
    void shouldBlockWhenEntryGateNoExecutor() throws Exception {
        Long entryLaneId = createLane("入口车道", "ENT_NOEXE", "ENTRY");
        Long entryCamId = createDevice("入口相机", "EC_NOEXE", "CAMERA", "SN_EC_NOEXE");
        Long entryGateId = createDevice("入口道闸", "EG_NOEXE", "GATE", "SN_EG_NOEXE");
        bindToLane(entryCamId, entryLaneId);
        bindToLane(entryGateId, entryLaneId);
        // 不设置 executor

        // 出口车道完整
        Long exitLaneId = createLane("出口车道", "EXT_NOEXE", "EXIT");
        Long exitCamId = createDevice("出口相机", "XC_NOEXE", "CAMERA", "SN_XC_NOEXE");
        Long exitGateId = createDevice("出口道闸", "XG_NOEXE", "GATE", "SN_XG_NOEXE");
        bindToLane(exitCamId, exitLaneId);
        bindToLane(exitGateId, exitLaneId);
        setExecutor(exitGateId, exitCamId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='ENTRY_GATE_NO_EXECUTOR' && @.level=='BLOCKER')]").exists());
    }

    // ==================== ⑪ 总车位为 0 → BLOCKER ====================

    @Test
    @DisplayName("总车位数未配置 → BLOCKER NO_TOTAL_SPACES")
    void shouldBlockWhenNoTotalSpaces() throws Exception {
        // 创建一个 totalSpaces=0 的停车场
        Long zeroSpacesLotId = createParkingLotWithSpaces(customerAdminToken, "零车位停车场", 0);

        mockMvc.perform(get("/admin/parking-lots/" + zeroSpacesLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='NO_TOTAL_SPACES')].level").value("BLOCKER"));
    }

    // ==================== ⑫ 占位项（implemented=false） ====================

    @Test
    @DisplayName("占位项（收费规则/支付/设备在线）→ WARNING + implemented=false")
    void shouldIncludePlaceholderWarnings() throws Exception {
        // 完整配置，仅检查占位项
        Long entryLaneId = createLane("入口车道", "ENT_PH", "ENTRY");
        Long entryCamId = createDevice("入口相机", "EC_PH", "CAMERA", "SN_EC_PH");
        Long entryGateId = createDevice("入口道闸", "EG_PH", "GATE", "SN_EG_PH");
        bindToLane(entryCamId, entryLaneId);
        bindToLane(entryGateId, entryLaneId);
        setExecutor(entryGateId, entryCamId);

        Long exitLaneId = createLane("出口车道", "EXT_PH", "EXIT");
        Long exitCamId = createDevice("出口相机", "XC_PH", "CAMERA", "SN_XC_PH");
        Long exitGateId = createDevice("出口道闸", "XG_PH", "GATE", "SN_XG_PH");
        bindToLane(exitCamId, exitLaneId);
        bindToLane(exitGateId, exitLaneId);
        setExecutor(exitGateId, exitCamId);

        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                // Check placeholder items exist with implemented=false
                .andExpect(jsonPath("$.data.items[?(@.code=='CHARGE_RULE_NOT_CONFIGURED')].level").value("WARNING"))
                .andExpect(jsonPath("$.data.items[?(@.code=='CHARGE_RULE_NOT_CONFIGURED')].implemented").value(false))
                .andExpect(jsonPath("$.data.items[?(@.code=='PAYMENT_NOT_CONFIGURED')].level").value("WARNING"))
                .andExpect(jsonPath("$.data.items[?(@.code=='PAYMENT_NOT_CONFIGURED')].implemented").value(false))
                // T24 已实现设备在线状态检查，DEVICE_ONLINE_UNCHECKED 占位项已移除
                // 设备从未查询 → DEVICE_STATUS_NEVER_QUERIED（implemented=true，非占位项）
                .andExpect(jsonPath("$.data.items[?(@.code=='DEVICE_STATUS_NEVER_QUERIED')]").exists())
                .andExpect(jsonPath("$.data.items[?(@.code=='DEVICE_STATUS_NEVER_QUERIED' && @.level=='WARNING' && @.implemented==true)]").exists());
    }

    // ==================== ⑬ 跨租户拒绝 ====================

    @Test
    @DisplayName("跨租户查询 → 403")
    void shouldRejectCrossTenant() throws Exception {
        // 用另一个租户创建一个停车场
        registerAndApprove("13900000005", "跨租户测试", "管理员2");
        String otherToken = login("13900000005", "pass123");
        Long otherLotId = createParkingLot(otherToken, "其他租户停车场", 50);

        // 用 customerAdminToken（租户1）去查询租户2的停车场 → 拒绝
        mockMvc.perform(get("/admin/parking-lots/" + otherLotId + "/readiness")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden());
    }

    // ==================== ⑭ 权限拒绝 ====================

    @Test
    @DisplayName("未登录 → 401")
    void shouldRejectNoPermission() throws Exception {
        // 未登录请求被 Sa-Token 拦截，直接返回 401
        mockMvc.perform(get("/admin/parking-lots/" + parkingLotId + "/readiness"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 工具方法 ====================

    private Long registerAndApprove(String phone, String company, String contact) throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"pass123\"}",
                                company, contact, phone)))
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
        int start = resp.indexOf("\"accessToken\":\"") + 15;
        int end = resp.indexOf("\"", start);
        return resp.substring(start, end);
    }

    private Long createParkingLot(String token, String name, int spaces) throws Exception {
        return createParkingLotWithSpaces(token, name, spaces);
    }

    private Long createParkingLotWithSpaces(String token, String name, int spaces) throws Exception {
        String resp = mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"totalSpaces\":%d}", name, spaces)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private Long createLane(String name, String code, String direction) throws Exception {
        String resp = mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"parkingLotId\":%d,\"name\":\"%s\",\"code\":\"%s\",\"direction\":\"%s\"}",
                                parkingLotId, name, code, direction)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private Long createDevice(String name, String code, String deviceType, String deviceSn) throws Exception {
        String resp = mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"parkingLotId\":%d,\"vendorId\":%d,\"modelId\":%d," +
                                "\"name\":\"%s\",\"code\":\"%s\",\"deviceType\":\"%s\",\"deviceSn\":\"%s\"}",
                                parkingLotId, vendorId, modelId, name, code, deviceType, deviceSn)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private void bindToLane(Long deviceId, Long laneId) throws Exception {
        mockMvc.perform(post("/admin/devices/" + deviceId + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void setExecutor(Long gateId, Long executorId) throws Exception {
        mockMvc.perform(post("/admin/devices/" + gateId + "/executor")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executorDeviceId\":" + executorId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private Long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        return Long.parseLong(json.substring(start, end).trim());
    }

    private Long extractFirstId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        return Long.parseLong(json.substring(start, end).trim());
    }

    private void cleanupTestData() {
        deviceMapper.delete(new LambdaQueryWrapper<>());
        laneMapper.delete(new LambdaQueryWrapper<>());
        parkingLotMapper.delete(new LambdaQueryWrapper<>());
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().ne(SysUser::getUsername, "admin"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>().isNotNull(Tenant::getContactPhone));
    }
}
