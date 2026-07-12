package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.DeviceVendor;
import com.jushan.system.entity.Tenant;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.DeviceMapper;
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
 * 设备厂商、型号与平台设备台账集成测试（T20）。
 * <p>
 * 权限说明：
 * <ul>
 *   <li>super_admin (adminToken)：拥有 device:read + device:manage，用于所有写操作</li>
 *   <li>customer_admin (customerAdminToken)：仅有 device:read，用于读操作和跨租户拒绝测试</li>
 *   <li>booth_operator：无设备权限，用于权限拒绝测试</li>
 * </ul>
 * <p>
 * 覆盖：
 * <ul>
 *   <li>创建设备（停车场归属、厂商/型号校验、编码唯一性、SN 同厂商唯一、设备类型合法性）</li>
 *   <li>查询设备列表和详情（租户隔离、类型/状态筛选）</li>
 *   <li>更新设备基础信息（部分更新、编码冲突）</li>
 *   <li>启用/停用设备（条件更新）</li>
 *   <li>跨租户拒绝</li>
 *   <li>空 SN 拒绝</li>
 *   <li>厂商/型号不匹配拒绝</li>
 *   <li>权限校验</li>
 *   <li>厂商/型号查询</li>
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
@DisplayName("设备厂商、型号与平台设备台账集成测试")
class DeviceControllerIntegrationTest extends TestcontainersBaseTest {

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
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceVendorMapper vendorMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private String parkingManagerToken;
    private Long parkingLotId;
    private Long vendorId;
    private Long modelId;

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
        registerAndApprove("13900000001", "T20测试租户有限公司", "客户管理员");

        // 4. 登录客户管理员并创建停车场
        customerAdminToken = login("13900000001", "pass123");
        parkingLotId = createParkingLotViaApi(customerAdminToken, "T20测试停车场", 100);

        // 5. 获取种子数据中的厂商和型号（臻识 C5H）
        DeviceVendor vendor = vendorMapper.selectOne(
                new LambdaQueryWrapper<DeviceVendor>().eq(DeviceVendor::getCode, "ZHENSHI"));
        vendorId = vendor.getId();

        // 查询型号 ID
        String modelsJson = mockMvc.perform(get("/admin/devices/models?vendorId=" + vendorId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        modelId = extractFirstId(modelsJson);

        // FIX-12：创建无 device:manage 的 parking_manager 用于权限拒绝测试
        createEmployee("T20运维", "13900000003", "parking_manager", parkingLotId);
        parkingManagerToken = login("13900000003", "pass123");
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ==================== ① 创建设备（写操作使用 adminToken） ====================

    @Test
    @DisplayName("正常创建相机设备 → 状态 ENABLED，类型 CAMERA")
    void shouldCreateCameraDevice() throws Exception {
        String resp = mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"入口相机1\",\"code\":\"CAM_01\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"ZHENSHI_C5H_SN_001\"," +
                                "\"capabilities\":\"RECOGNIZE,CAPTURE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("入口相机1"))
                .andExpect(jsonPath("$.data.code").value("CAM_01"))
                .andExpect(jsonPath("$.data.deviceType").value("CAMERA"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.parkingLotId").value(parkingLotId))
                .andExpect(jsonPath("$.data.vendorName").value("臻识"))
                .andExpect(jsonPath("$.data.modelName").value("C5H"))
                .andReturn().getResponse().getContentAsString();

        // 验证数据库
        Long deviceId = extractId(resp);
        Device device = deviceMapper.selectById(deviceId);
        assertThat(device).isNotNull();
        assertThat(device.getParkingLotId()).isEqualTo(parkingLotId);
        assertThat(device.getVendorId()).isEqualTo(vendorId);
        assertThat(device.getModelId()).isEqualTo(modelId);
        assertThat(device.getCode()).isEqualTo("CAM_01");
        assertThat(device.getDeviceSn()).isEqualTo("ZHENSHI_C5H_SN_001");
        assertThat(device.getDeviceType()).isEqualTo("CAMERA");
        assertThat(device.getStatus()).isEqualTo("ENABLED");
        assertThat(device.getCapabilities()).isEqualTo("RECOGNIZE,CAPTURE");
    }

    @Test
    @DisplayName("创建道闸设备 → 类型 GATE")
    void shouldCreateGateDevice() throws Exception {
        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"出口道闸1\",\"code\":\"GATE_01\"," +
                                "\"deviceType\":\"GATE\",\"deviceSn\":\"ZHENSHI_GATE_SN_001\"," +
                                "\"capabilities\":\"OPEN,CLOSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceType").value("GATE"))
                .andExpect(jsonPath("$.data.capabilities").value("OPEN,CLOSE"));
    }

    @Test
    @DisplayName("设备名称为空 → 400（校验通过权限后由 @Valid 拒绝）")
    void shouldRejectEmptyName() throws Exception {
        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"\",\"code\":\"D_01\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"SN_EMPTY_NAME\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("非法设备类型 → 400")
    void shouldRejectInvalidDeviceType() throws Exception {
        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"非法类型\",\"code\":\"INV_TYPE\"," +
                                "\"deviceType\":\"SENSOR\",\"deviceSn\":\"SN_INV_TYPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("无效的设备类型")));
    }

    @Test
    @DisplayName("同停车场重复编码 → 拒绝（业务 1000）")
    void shouldRejectDuplicateCode() throws Exception {
        createDeviceViaApi("CODE_A", "DUPE_CODE", "CAMERA", "SN_DUPE_A");

        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"设备B\",\"code\":\"DUPE_CODE\"," +
                                "\"deviceType\":\"GATE\",\"deviceSn\":\"SN_DUPE_B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已存在")));
    }

    @Test
    @DisplayName("同厂商重复 device_sn → 拒绝（业务 1000）")
    void shouldRejectDuplicateSn() throws Exception {
        createDeviceViaApi("设备A", "CODE_A", "CAMERA", "SN_DUPE_001");

        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"设备B\",\"code\":\"CODE_B\"," +
                                "\"deviceType\":\"GATE\",\"deviceSn\":\"SN_DUPE_001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已存在")));
    }

    @Test
    @DisplayName("空 device_sn → 400（校验通过权限后由 @Valid 拒绝）")
    void shouldRejectEmptySn() throws Exception {
        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"空SN设备\",\"code\":\"EMPTY_SN\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("不同停车场相同编码 → 允许")
    void shouldAllowSameCodeInDifferentParkingLots() throws Exception {
        Long lotId2 = createParkingLotViaApi(customerAdminToken, "编码测试停车场2", 200);

        createDeviceViaApi("设备A", "SAME_CODE", "CAMERA", "SN_SAME_CODE_A");
        createDeviceViaApiForLot(lotId2, "设备B", "SAME_CODE", "CAMERA", "SN_SAME_CODE_B");

        // 两个停车场各自有一个编码为 SAME_CODE 的设备，均应查询到
        mockMvc.perform(get("/admin/devices?page=1&size=20&parkingLotId=" + parkingLotId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value("SAME_CODE"));

        mockMvc.perform(get("/admin/devices?page=1&size=20&parkingLotId=" + lotId2)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value("SAME_CODE"));
    }

    @Test
    @DisplayName("停车场不存在 → 404")
    void shouldRejectNonexistentParkingLot() throws Exception {
        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":99999,\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"幽灵设备\",\"code\":\"GHOST\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"SN_GHOST\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("停车场不存在")));
    }

    @Test
    @DisplayName("厂商不存在 → 404")
    void shouldRejectNonexistentVendor() throws Exception {
        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":99999," +
                                "\"modelId\":" + modelId + ",\"name\":\"幽灵厂商\",\"code\":\"BAD_VENDOR\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"SN_BAD_VENDOR\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("设备厂商不存在")));
    }

    @Test
    @DisplayName("型号不属于该厂商 → 拒绝（业务 1000）")
    void shouldRejectModelMismatch() throws Exception {
        DeviceVendor xinluton = vendorMapper.selectOne(
                new LambdaQueryWrapper<DeviceVendor>().eq(DeviceVendor::getCode, "XINLUTONG"));

        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + xinluton.getId() +
                                ",\"modelId\":" + modelId + ",\"name\":\"型号不匹配\",\"code\":\"MISMATCH\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"SN_MISMATCH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("设备型号不属于所选厂商")));
    }

    // ==================== ② 查询设备（读操作使用 customerAdminToken） ====================

    @Test
    @DisplayName("查询设备列表 → 按停车场返回")
    void shouldListDevicesByParkingLot() throws Exception {
        createDeviceViaApi("设备1", "D01", "CAMERA", "SN_D01");
        createDeviceViaApi("设备2", "D02", "CAMERA", "SN_D02");

        mockMvc.perform(get("/admin/devices?page=1&size=20&parkingLotId=" + parkingLotId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("按设备类型筛选 → 仅返回匹配类型")
    void shouldFilterByDeviceType() throws Exception {
        createDeviceViaApi("相机1", "CAM01", "CAMERA", "SN_CAM01");
        createDeviceViaApi("道闸1", "GAT01", "GATE", "SN_GAT01");

        mockMvc.perform(get("/admin/devices?page=1&size=20&parkingLotId=" + parkingLotId + "&deviceType=CAMERA")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].deviceType").value("CAMERA"));
    }

    @Test
    @DisplayName("按状态筛选 → 仅返回匹配状态")
    void shouldFilterByStatus() throws Exception {
        createDeviceViaApi("启用设备", "STA01", "CAMERA", "SN_STA01");
        Long deviceId2 = createDeviceViaApi("停用设备", "STA02", "CAMERA", "SN_STA02");

        // 停用第二个（admin 有 device:manage 权限）
        mockMvc.perform(post("/admin/devices/" + deviceId2 + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(get("/admin/devices?page=1&size=20&parkingLotId=" + parkingLotId + "&status=ENABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("ENABLED"));
    }

    @Test
    @DisplayName("查询设备详情 → 返回完整信息（含厂商和型号名称）")
    void shouldGetDeviceDetail() throws Exception {
        Long deviceId = createDeviceViaApi("详情设备", "DETAIL", "CAMERA", "SN_DETAIL");

        mockMvc.perform(get("/admin/devices/" + deviceId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("详情设备"))
                .andExpect(jsonPath("$.data.vendorName").value("臻识"))
                .andExpect(jsonPath("$.data.modelName").value("C5H"))
                .andExpect(jsonPath("$.data.deviceSn").value("SN_DETAIL"))
                .andExpect(jsonPath("$.data.deviceType").value("CAMERA"));
    }

    @Test
    @DisplayName("跨租户查询设备 → 403")
    void shouldRejectCrossTenantDevice() throws Exception {
        // 创建另一个租户并创建设备（admin 代为创建）
        registerAndApprove("13900000002", "跨查租户T20", "其他管理员");
        String otherToken = login("13900000002", "pass123");
        Long otherLotId = createParkingLotViaApi(otherToken, "其他租户停车场", 200);
        Long otherDeviceId = createDeviceViaApiForLot(otherLotId, "他人设备", "OTHER_DEV", "CAMERA", "SN_OTHER");

        // 本租户管理员查询 → 403
        mockMvc.perform(get("/admin/devices/" + otherDeviceId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    // ==================== ③ 更新设备（写操作使用 adminToken） ====================

    @Test
    @DisplayName("更新设备名称和备注 → 成功")
    void shouldUpdateDevice() throws Exception {
        Long deviceId = createDeviceViaApi("原名", "UPD01", "CAMERA", "SN_UPD01");

        mockMvc.perform(put("/admin/devices/" + deviceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名\",\"description\":\"更新备注\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名"))
                .andExpect(jsonPath("$.data.description").value("更新备注"))
                .andExpect(jsonPath("$.data.deviceType").value("CAMERA")); // 未修改

        Device updated = deviceMapper.selectById(deviceId);
        assertThat(updated.getName()).isEqualTo("新名");
        assertThat(updated.getDescription()).isEqualTo("更新备注");
    }

    @Test
    @DisplayName("更新编码冲突 → 拒绝")
    void shouldRejectCodeConflict() throws Exception {
        createDeviceViaApi("冲突设备A", "CONF_A", "CAMERA", "SN_CONF_A");
        Long deviceId2 = createDeviceViaApi("冲突设备B", "CONF_B", "CAMERA", "SN_CONF_B");

        mockMvc.perform(put("/admin/devices/" + deviceId2)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CONF_A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已存在")));
    }

    // ==================== ④ 启用/停用（写操作使用 adminToken） ====================

    @Test
    @DisplayName("停用设备 → 成功")
    void shouldDisableDevice() throws Exception {
        Long deviceId = createDeviceViaApi("可停设备", "DIS01", "CAMERA", "SN_DIS01");

        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Device device = deviceMapper.selectById(deviceId);
        assertThat(device.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("重新启用设备 → 成功")
    void shouldReEnableDevice() throws Exception {
        Long deviceId = createDeviceViaApi("可启设备", "ENA01", "CAMERA", "SN_ENA01");

        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLED\"}"))
                .andExpect(status().isOk());

        Device device = deviceMapper.selectById(deviceId);
        assertThat(device.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("重复停用 → 拒绝")
    void shouldRejectDoubleDisable() throws Exception {
        Long deviceId = createDeviceViaApi("双停设备", "DBL01", "CAMERA", "SN_DBL01");

        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已")));
    }

    @Test
    @DisplayName("无效状态操作 → 400")
    void shouldRejectInvalidStatusAction() throws Exception {
        Long deviceId = createDeviceViaApi("无效操作设备", "INV01", "CAMERA", "SN_INV01");

        mockMvc.perform(post("/admin/devices/" + deviceId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DELETE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("无效")));
    }

    // ==================== ⑤ 权限校验 ====================

    @Test
    @DisplayName("未登录访问 → 401")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/devices?parkingLotId=" + parkingLotId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("无权限角色（booth_operator）创建设备 → 403")
    void shouldRejectNoPermission() throws Exception {
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"岗亭操作员\",\"phone\":\"13911110001\"," +
                        "\"password\":\"pass123\",\"roleCode\":\"booth_operator\"," +
                        "\"parkingLotIds\":[" + parkingLotId + "]}"));
        String boothToken = login("13911110001", "pass123");

        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + boothToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"越权设备\",\"code\":\"NO_PERM\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"SN_NO_PERM\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("有 device:read 无 device:manage 创建设备 → 403（FIX-12：使用 parking_manager，其仅有 device:read）")
    void shouldRejectCustomerAdminCreate() throws Exception {
        // parking_manager 仅有 device:read，无 device:manage
        mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + parkingManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"vendorId\":" + vendorId +
                                ",\"modelId\":" + modelId + ",\"name\":\"客户管理越权\",\"code\":\"CUST_PERM\"," +
                                "\"deviceType\":\"CAMERA\",\"deviceSn\":\"SN_CUST_PERM\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无权限角色更新设备 → 403（FIX-12：使用 parking_manager）")
    void shouldRejectNoPermissionUpdate() throws Exception {
        Long deviceId = createDeviceViaApi("权限测试设备", "PERM01", "CAMERA", "SN_PERM01");

        mockMvc.perform(put("/admin/devices/" + deviceId)
                        .header("Authorization", "Bearer " + parkingManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权修改\"}"))
                .andExpect(status().isForbidden());
    }

    // ==================== ⑥ 平台用户跨租户 ====================

    @Test
    @DisplayName("平台用户可查看任意租户设备")
    void shouldAllowPlatformUserToViewAnyDevice() throws Exception {
        Long deviceId = createDeviceViaApi("平台可查设备", "PLAT01", "CAMERA", "SN_PLAT01");

        mockMvc.perform(get("/admin/devices/" + deviceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("平台可查设备"));
    }

    // ==================== ⑦ 厂商/型号查询 ====================

    @Test
    @DisplayName("查询厂商列表 → 返回种子数据（臻识、信路通）")
    void shouldListVendors() throws Exception {
        mockMvc.perform(get("/admin/devices/vendors")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("ZHENSHI"))
                .andExpect(jsonPath("$.data[1].code").value("XINLUTONG"));
    }

    @Test
    @DisplayName("查询型号列表（按厂商筛选）→ 返回臻识 C5H")
    void shouldListModelsByVendor() throws Exception {
        mockMvc.perform(get("/admin/devices/models?vendorId=" + vendorId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("C5H"))
                .andExpect(jsonPath("$.data[0].deviceType").value("CAMERA"));
    }

    @Test
    @DisplayName("查询全部型号列表 → 不传 vendorId")
    void shouldListAllModels() throws Exception {
        mockMvc.perform(get("/admin/devices/models")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1)); // 只有 C5H
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
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, phone));

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

    /** 使用 adminToken 在默认停车场创建设备 */
    private Long createDeviceViaApi(String name, String code, String deviceType, String deviceSn) throws Exception {
        return createDeviceWithToken(adminToken, parkingLotId, name, code, deviceType, deviceSn);
    }

    /** 使用 adminToken 在指定停车场创建设备 */
    private Long createDeviceViaApiForLot(Long lId, String name, String code, String deviceType, String deviceSn) throws Exception {
        return createDeviceWithToken(adminToken, lId, name, code, deviceType, deviceSn);
    }

    private Long createDeviceWithToken(String token, Long lId, String name, String code,
                                        String deviceType, String deviceSn) throws Exception {
        String resp = mockMvc.perform(post("/admin/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"parkingLotId\":%d,\"vendorId\":%d,\"modelId\":%d," +
                                        "\"name\":\"%s\",\"code\":\"%s\",\"deviceType\":\"%s\",\"deviceSn\":\"%s\"}",
                                lId, vendorId, modelId, name, code, deviceType, deviceSn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return extractId(resp);
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private Long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        String idStr = json.substring(start, end).trim();
        return Long.parseLong(idStr);
    }

    private Long extractFirstId(String json) {
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
        deviceMapper.delete(new LambdaQueryWrapper<>());
        parkingLotMapper.delete(new LambdaQueryWrapper<>());
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().ne(SysUser::getUsername, "admin"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>().isNotNull(Tenant::getContactPhone));
    }
}
