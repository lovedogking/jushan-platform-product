package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.Device;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 设备-车道绑定集成测试（T21）。
 * <p>
 * 覆盖：绑定、解绑、跨停车场拒绝、禁用设备拒绝、同类型重复拒绝、执行相机设置、
 * 车道详情含设备、列表含设备分组。
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
@DisplayName("设备-车道绑定集成测试")
class LaneDeviceBindingIntegrationTest extends TestcontainersBaseTest {

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
    private String parkingManagerToken;
    private Long parkingLotId;
    private Long vendorId;
    private Long modelId;
    private Long laneId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        cleanupTestData();

        registerAndApprove("13900000003", "T21测试租户", "管理员");
        customerAdminToken = login("13900000003", "pass123");
        parkingLotId = createParkingLot(customerAdminToken, "T21测试停车场", 100);

        DeviceVendor vendor = vendorMapper.selectOne(
                new LambdaQueryWrapper<DeviceVendor>().eq(DeviceVendor::getCode, "ZHENSHI"));
        vendorId = vendor.getId();
        modelId = extractFirstId(
                mockMvc.perform(get("/admin/devices/models?vendorId=" + vendorId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        // 创建一条车道
        laneId = createLane(customerAdminToken, "测试车道1", "LANE_01", "ENTRY");

        // FIX-12：创建无 device:manage 的 parking_manager 用于"无权限拒绝"测试
        createEmployee("T21运维", "13900000004", "parking_manager", parkingLotId);
        parkingManagerToken = login("13900000004", "pass123");
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ==================== ① 相机绑定车道 ====================

    @Test
    @DisplayName("相机绑定车道 → 成功，车道详情返回该相机")
    void shouldBindCameraToLane() throws Exception {
        Long cameraId = createDevice(adminToken, "入口相机", "CAM_BIND", "CAMERA", "SN_CAM_BIND");

        mockMvc.perform(post("/admin/devices/" + cameraId + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.laneId").value(laneId));

        // 验证数据库
        Device device = deviceMapper.selectById(cameraId);
        assertThat(device.getLaneId()).isEqualTo(laneId);

        // 车道详情应包含该相机
        mockMvc.perform(get("/admin/lanes/" + laneId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devices.length()").value(1))
                .andExpect(jsonPath("$.data.devices[0].id").value(cameraId))
                .andExpect(jsonPath("$.data.devices[0].deviceType").value("CAMERA"));
    }

    // ==================== ② 道闸绑定车道 + 执行相机 ====================

    @Test
    @DisplayName("道闸绑定车道并设置执行相机 → 成功")
    void shouldBindGateAndSetExecutor() throws Exception {
        Long cameraId = createDevice(adminToken, "入口相机", "CAM_EXE", "CAMERA", "SN_CAM_EXE");
        Long gateId = createDevice(adminToken, "入口道闸", "GAT_EXE", "GATE", "SN_GAT_EXE");

        // 绑定相机到车道
        mockMvc.perform(post("/admin/devices/" + cameraId + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isOk());

        // 绑定道闸到车道
        mockMvc.perform(post("/admin/devices/" + gateId + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isOk());

        // 设置执行相机
        mockMvc.perform(post("/admin/devices/" + gateId + "/executor")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executorDeviceId\":" + cameraId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executorDeviceId").value(cameraId));

        Device gate = deviceMapper.selectById(gateId);
        assertThat(gate.getExecutorDeviceId()).isEqualTo(cameraId);

        // 车道详情应包含两个设备
        mockMvc.perform(get("/admin/lanes/" + laneId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devices.length()").value(2));
    }

    // ==================== ③ 解绑 ====================

    @Test
    @DisplayName("解绑设备 → 成功，laneId 和 executorDeviceId 均清空")
    void shouldUnbindDevice() throws Exception {
        Long cameraId = createDevice(adminToken, "解绑相机", "UNBIND1", "CAMERA", "SN_UNBIND1");
        Long gateId = createDevice(adminToken, "解绑道闸", "UNBIND2", "GATE", "SN_UNBIND2");

        mockMvc.perform(post("/admin/devices/" + cameraId + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));
        mockMvc.perform(post("/admin/devices/" + gateId + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));
        mockMvc.perform(post("/admin/devices/" + gateId + "/executor")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executorDeviceId\":" + cameraId + "}"));

        // 解绑道闸
        mockMvc.perform(delete("/admin/devices/" + gateId + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.laneId").isEmpty())
                .andExpect(jsonPath("$.data.executorDeviceId").isEmpty());

        Device gate = deviceMapper.selectById(gateId);
        assertThat(gate.getLaneId()).isNull();
        assertThat(gate.getExecutorDeviceId()).isNull();

        // 车道详情只剩相机
        mockMvc.perform(get("/admin/lanes/" + laneId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devices.length()").value(1));
    }

    // ==================== ④ 约束校验 ====================

    @Test
    @DisplayName("同车道同类型重复绑定 → 拒绝")
    void shouldRejectDuplicateTypeBinding() throws Exception {
        Long cam1 = createDevice(adminToken, "相机1", "DUP1", "CAMERA", "SN_DUP1");
        Long cam2 = createDevice(adminToken, "相机2", "DUP2", "CAMERA", "SN_DUP2");

        mockMvc.perform(post("/admin/devices/" + cam1 + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/devices/" + cam2 + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("该车道已绑定了一台 CAMERA 设备，不允许重复绑定"));
    }

    @Test
    @DisplayName("跨停车场绑定 → 拒绝")
    void shouldRejectCrossParkingLotBinding() throws Exception {
        Long otherLotId = createParkingLot(customerAdminToken, "另一停车场", 200);
        Long otherLaneId = createLaneForLot(customerAdminToken, otherLotId, "另一车道", "LANE_X", "ENTRY");
        Long cameraId = createDevice(adminToken, "跨场相机", "CROSS1", "CAMERA", "SN_CROSS1");

        mockMvc.perform(post("/admin/devices/" + cameraId + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + otherLaneId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("设备与车道不属于同一停车场，不允许绑定"));
    }

    @Test
    @DisplayName("禁用设备绑定 → 拒绝")
    void shouldRejectDisabledDeviceBinding() throws Exception {
        Long cameraId = createDevice(adminToken, "禁用相机", "DIS_CAM", "CAMERA", "SN_DIS_CAM");

        mockMvc.perform(post("/admin/devices/" + cameraId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(post("/admin/devices/" + cameraId + "/bind-lane")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("已停用的设备不能绑定车道"));
    }

    @Test
    @DisplayName("执行设备非 CAMERA → 拒绝")
    void shouldRejectNonCameraExecutor() throws Exception {
        Long gate1 = createDevice(adminToken, "道闸1", "GAT_EX1", "GATE", "SN_GAT_EX1");
        Long gate2 = createDevice(adminToken, "道闸2", "GAT_EX2", "GATE", "SN_GAT_EX2");

        mockMvc.perform(post("/admin/devices/" + gate1 + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));
        mockMvc.perform(post("/admin/devices/" + gate2 + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));

        // gate2 是 GATE，不能作为 gate1 的 executor
        mockMvc.perform(post("/admin/devices/" + gate1 + "/executor")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executorDeviceId\":" + gate2 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("执行设备必须是相机（CAMERA）类型"));
    }

    @Test
    @DisplayName("执行相机与 GATE 不同车道 → 拒绝")
    void shouldRejectExecutorDifferentLane() throws Exception {
        Long otherLaneId = createLane(customerAdminToken, "另一车道", "LANE_Y", "EXIT");
        Long cameraId = createDevice(adminToken, "相机", "CAM_DL", "CAMERA", "SN_CAM_DL");
        Long gateId = createDevice(adminToken, "道闸", "GAT_DL", "GATE", "SN_GAT_DL");

        mockMvc.perform(post("/admin/devices/" + cameraId + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + otherLaneId + "}"));
        mockMvc.perform(post("/admin/devices/" + gateId + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));

        mockMvc.perform(post("/admin/devices/" + gateId + "/executor")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executorDeviceId\":" + cameraId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("GATE 设备与执行相机必须绑定到同一车道"));
    }

    @Test
    @DisplayName("CAMERA 设备设置 executor → 拒绝")
    void shouldRejectExecutorOnCamera() throws Exception {
        Long cam1 = createDevice(adminToken, "相机1", "CAM_EXR", "CAMERA", "SN_CAM_EXR");
        Long cam2 = createDevice(adminToken, "相机2", "CAM_EXR2", "CAMERA", "SN_CAM_EXR2");

        mockMvc.perform(post("/admin/devices/" + cam1 + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));
        mockMvc.perform(post("/admin/devices/" + cam2 + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));

        mockMvc.perform(post("/admin/devices/" + cam1 + "/executor")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executorDeviceId\":" + cam2 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("仅逻辑道闸（GATE）设备需要设置执行相机"));
    }

    // ==================== ⑤ 车道列表含设备 ====================

    @Test
    @DisplayName("车道列表返回绑定设备（按 laneId 分组）")
    void shouldIncludeDevicesInLaneList() throws Exception {
        Long laneId2 = createLane(customerAdminToken, "车道2", "LANE_02", "EXIT");
        Long cam1 = createDevice(adminToken, "相机1", "LL_CAM1", "CAMERA", "SN_LL_CAM1");
        Long gate1 = createDevice(adminToken, "道闸1", "LL_GATE1", "GATE", "SN_LL_GATE1");

        // 绑定到 laneId
        mockMvc.perform(post("/admin/devices/" + cam1 + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + "}"));

        // gate1 绑定到 laneId2
        mockMvc.perform(post("/admin/devices/" + gate1 + "/bind-lane")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId2 + "}"));

        mockMvc.perform(get("/admin/lanes?parkingLotId=" + parkingLotId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    // ==================== ⑥ 权限 ====================

    @Test
    @DisplayName("无权限绑定 → 403（FIX-12：使用 parking_manager，其仅有 device:read 无 device:manage）")
    void shouldRejectNoPermissionBind() throws Exception {
        Long cameraId = createDevice(adminToken, "权限相机", "PERM_CAM", "CAMERA", "SN_PERM_CAM");

        mockMvc.perform(post("/admin/devices/" + cameraId + "/bind-lane")
                        .header("Authorization", "Bearer " + parkingManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"laneId\":" + laneId + "}"))
                .andExpect(status().isForbidden());
    }

    // ==================== ⑦ laneId 空 null → 不过滤（MySQL UNIQUE 允许多个 NULL） ====================

    @Test
    @DisplayName("未绑定设备 laneId 为 null → 不计入 UNIQUE 约束")
    void shouldAllowMultipleUnboundDevices() throws Exception {
        Long cam1 = createDevice(adminToken, "未绑1", "UNB_A", "CAMERA", "SN_UNB_A");
        Long cam2 = createDevice(adminToken, "未绑2", "UNB_B", "CAMERA", "SN_UNB_B");

        // 两个 CAMERA 都不绑定，laneId 均为 NULL → 不应冲突
        assertThat(deviceMapper.selectById(cam1).getLaneId()).isNull();
        assertThat(deviceMapper.selectById(cam2).getLaneId()).isNull();
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
        String resp = mockMvc.perform(post("/admin/parking-lots")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"totalSpaces\":%d}", name, spaces)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private Long createLane(String token, String name, String code, String direction) throws Exception {
        return createLaneForLot(token, parkingLotId, name, code, direction);
    }

    private Long createLaneForLot(String token, Long lotId, String name, String code, String direction) throws Exception {
        String resp = mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"parkingLotId\":%d,\"name\":\"%s\",\"code\":\"%s\",\"direction\":\"%s\"}",
                                lotId, name, code, direction)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private Long createDevice(String token, String name, String code, String deviceType, String deviceSn) throws Exception {
        String resp = mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"parkingLotId\":%d,\"vendorId\":%d,\"modelId\":%d," +
                                        "\"name\":\"%s\",\"code\":\"%s\",\"deviceType\":\"%s\",\"deviceSn\":\"%s\"}",
                                parkingLotId, vendorId, modelId, name, code, deviceType, deviceSn)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
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
        deviceMapper.delete(new LambdaQueryWrapper<>());
        laneMapper.delete(new LambdaQueryWrapper<>());
        parkingLotMapper.delete(new LambdaQueryWrapper<>());
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().ne(SysUser::getUsername, "admin"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>().isNotNull(Tenant::getContactPhone));
    }
}
