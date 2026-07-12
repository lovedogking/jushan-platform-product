package com.jushan.boot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.Tenant;
import com.jushan.system.entity.SysUser;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 入口、出口与车道模型集成测试（T19）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>创建车道（停车场归属、编码唯一性、方向合法性）</li>
 *   <li>查询车道列表和详情（租户隔离、方向/状态筛选）</li>
 *   <li>更新车道基础信息（部分更新、编码冲突）</li>
 *   <li>启用/停用车道</li>
 *   <li>跨租户拒绝</li>
 *   <li>权限校验</li>
 *   <li>非法方向拒绝</li>
 *   <li>关键车道标识</li>
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
@DisplayName("入口、出口与车道模型集成测试")
class ParkingLaneControllerIntegrationTest extends TestcontainersBaseTest {

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
    private ParkingLaneMapper laneMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String customerAdminToken;
    private Long tenantId;
    private Long parkingLotId;

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
        tenantId = registerAndApprove("13900000001", "T19测试租户有限公司", "客户管理员");

        // 4. 登录客户管理员并创建停车场
        customerAdminToken = login("13900000001", "pass123");
        parkingLotId = createParkingLotViaApi(customerAdminToken, "T19测试停车场", 100);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    // ==================== ① 创建车道 ====================

    @Test
    @DisplayName("正常创建入口车道 → 状态 ENABLED，方向 ENTRY")
    void shouldCreateEntryLane() throws Exception {
        String resp = mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"入口车道1\"," +
                                "\"code\":\"ENTRY_01\",\"direction\":\"ENTRY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("入口车道1"))
                .andExpect(jsonPath("$.data.code").value("ENTRY_01"))
                .andExpect(jsonPath("$.data.direction").value("ENTRY"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.parkingLotId").value(parkingLotId))
                .andReturn().getResponse().getContentAsString();

        // 验证数据库
        Long laneId = extractId(resp);
        ParkingLane lane = laneMapper.selectById(laneId);
        assertThat(lane).isNotNull();
        assertThat(lane.getParkingLotId()).isEqualTo(parkingLotId);
        assertThat(lane.getCode()).isEqualTo("ENTRY_01");
        assertThat(lane.getDirection()).isEqualTo("ENTRY");
        assertThat(lane.getStatus()).isEqualTo("ENABLED");
        assertThat(lane.getIsKeyLane()).isEqualTo(0); // 默认值
        assertThat(lane.getAutoReleasePolicy()).isEqualTo("MANUAL"); // 默认值
    }

    @Test
    @DisplayName("创建出口车道 → 方向 EXIT")
    void shouldCreateExitLane() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"出口车道1\"," +
                                "\"code\":\"EXIT_01\",\"direction\":\"EXIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.direction").value("EXIT"));
    }

    @Test
    @DisplayName("创建混合车道 → 方向 MIXED（可逆枚举，待确认）")
    void shouldCreateMixedLane() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"混合车道1\"," +
                                "\"code\":\"MIXED_01\",\"direction\":\"MIXED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.direction").value("MIXED"));
    }

    @Test
    @DisplayName("创建关键车道 → isKeyLane = 1")
    void shouldCreateKeyLane() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"关键入口\"," +
                                "\"code\":\"KEY_01\",\"direction\":\"ENTRY\",\"isKeyLane\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isKeyLane").value(1));
    }

    @Test
    @DisplayName("非法方向 → 400")
    void shouldRejectInvalidDirection() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"非法方向\"," +
                                "\"code\":\"INVALID_01\",\"direction\":\"UP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("无效的车道方向")));
    }

    @Test
    @DisplayName("车道名称为空 → 400")
    void shouldRejectEmptyName() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"\"," +
                                "\"code\":\"L_01\",\"direction\":\"ENTRY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("同停车场重复编码 → 拒绝（业务 1000）")
    void shouldRejectDuplicateCode() throws Exception {
        // 先创建一条
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"车道A\"," +
                                "\"code\":\"DUPE_01\",\"direction\":\"ENTRY\"}"))
                .andExpect(status().isOk());

        // 同编码再创建 → 拒绝
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"车道B\"," +
                                "\"code\":\"DUPE_01\",\"direction\":\"EXIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已存在")));
    }

    @Test
    @DisplayName("不同停车场相同编码 → 允许")
    void shouldAllowSameCodeInDifferentParkingLots() throws Exception {
        // 创建第二个停车场
        Long lotId2 = createParkingLotViaApi(customerAdminToken, "编码测试停车场2", 200);

        // 停车场 1 创建编码 L_001
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"车道A\"," +
                                "\"code\":\"L_001\",\"direction\":\"ENTRY\"}"))
                .andExpect(status().isOk());

        // 停车场 2 使用相同编码 → 允许
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + lotId2 + ",\"name\":\"车道B\"," +
                                "\"code\":\"L_001\",\"direction\":\"ENTRY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("停车场不存在 → 404")
    void shouldRejectNonexistentParkingLot() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":99999,\"name\":\"幽灵车道\"," +
                                "\"code\":\"GHOST_01\",\"direction\":\"ENTRY\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("停车场不存在")));
    }

    // ==================== ② 查询车道 ====================

    @Test
    @DisplayName("查询车道列表 → 按停车场返回")
    void shouldListLanesByParkingLot() throws Exception {
        // 创建两个车道
        createLaneViaApi(customerAdminToken, parkingLotId, "入口1", "E01", "ENTRY");
        createLaneViaApi(customerAdminToken, parkingLotId, "出口1", "X01", "EXIT");

        mockMvc.perform(get("/admin/lanes?page=1&size=20&parkingLotId=" + parkingLotId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    @DisplayName("按方向筛选 → 仅返回匹配方向")
    void shouldFilterByDirection() throws Exception {
        createLaneViaApi(customerAdminToken, parkingLotId, "入口A", "E02", "ENTRY");
        createLaneViaApi(customerAdminToken, parkingLotId, "出口A", "X02", "EXIT");

        mockMvc.perform(get("/admin/lanes?page=1&size=20&parkingLotId=" + parkingLotId + "&direction=ENTRY")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].direction").value("ENTRY"));
    }

    @Test
    @DisplayName("按状态筛选 → 仅返回匹配状态")
    void shouldFilterByStatus() throws Exception {
        createLaneViaApi(customerAdminToken, parkingLotId, "启用车道", "E03", "ENTRY");
        Long laneId2 = createLaneViaApi(customerAdminToken, parkingLotId, "停用车道路", "E04", "ENTRY");

        // 停用第二个
        mockMvc.perform(post("/admin/lanes/" + laneId2 + "/status")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(get("/admin/lanes?page=1&size=20&parkingLotId=" + parkingLotId + "&status=ENABLED")
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("ENABLED"));
    }

    @Test
    @DisplayName("查询车道详情 → 返回完整信息")
    void shouldGetLaneDetail() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "详情车道", "DETAIL_01", "ENTRY");

        mockMvc.perform(get("/admin/lanes/" + laneId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("详情车道"))
                .andExpect(jsonPath("$.data.direction").value("ENTRY"));
    }

    @Test
    @DisplayName("跨租户查询车道 → 403")
    void shouldRejectCrossTenantLane() throws Exception {
        // 创建另一个租户
        registerAndApprove("13900000002", "跨查租户2", "其他管理员2");
        String otherToken = login("13900000002", "pass123");
        Long otherLotId = createParkingLotViaApi(otherToken, "其他租户停车场", 200);
        Long otherLaneId = createLaneViaApi(otherToken, otherLotId, "他人车道", "OTHER_01", "ENTRY");

        // 本租户管理员查询 → 403
        mockMvc.perform(get("/admin/lanes/" + otherLaneId)
                        .header("Authorization", "Bearer " + customerAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("不属于本租户")));
    }

    // ==================== ③ 更新车道 ====================

    @Test
    @DisplayName("更新车道名称和备注 → 成功")
    void shouldUpdateLane() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "原名", "UPD_01", "ENTRY");

        mockMvc.perform(put("/admin/lanes/" + laneId)
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名\",\"description\":\"更新备注\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名"))
                .andExpect(jsonPath("$.data.description").value("更新备注"))
                .andExpect(jsonPath("$.data.direction").value("ENTRY")); // 未修改

        // 验证数据库
        ParkingLane updated = laneMapper.selectById(laneId);
        assertThat(updated.getName()).isEqualTo("新名");
        assertThat(updated.getDescription()).isEqualTo("更新备注");
    }

    @Test
    @DisplayName("更新编码冲突 → 拒绝")
    void shouldRejectCodeConflict() throws Exception {
        createLaneViaApi(customerAdminToken, parkingLotId, "冲突车道A", "CONFLICT_A", "ENTRY");
        Long laneId2 = createLaneViaApi(customerAdminToken, parkingLotId, "冲突车道B", "CONFLICT_B", "ENTRY");

        // 尝试将 laneId2 的编码改为 CONFLICT_A
        mockMvc.perform(put("/admin/lanes/" + laneId2)
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CONFLICT_A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已存在")));
    }

    @Test
    @DisplayName("更新为自己的编码 → 允许（幂等）")
    void shouldAllowUpdatingToSameCode() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "幂等车道", "IDEM_01", "ENTRY");

        mockMvc.perform(put("/admin/lanes/" + laneId)
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"IDEM_01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== ④ 启用/停用 ====================

    @Test
    @DisplayName("停用车道 → 成功")
    void shouldDisableLane() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "可停车道", "DIS_01", "ENTRY");

        mockMvc.perform(post("/admin/lanes/" + laneId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ParkingLane lane = laneMapper.selectById(laneId);
        assertThat(lane.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("重新启用车道 → 成功")
    void shouldReEnableLane() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "可启车道", "ENA_01", "ENTRY");

        // 先停用
        mockMvc.perform(post("/admin/lanes/" + laneId + "/status")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        // 再启用
        mockMvc.perform(post("/admin/lanes/" + laneId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLED\"}"))
                .andExpect(status().isOk());

        ParkingLane lane = laneMapper.selectById(laneId);
        assertThat(lane.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("重复停用 → 拒绝")
    void shouldRejectDoubleDisable() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "双停车道", "DBL_01", "ENTRY");

        mockMvc.perform(post("/admin/lanes/" + laneId + "/status")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\"}"));

        mockMvc.perform(post("/admin/lanes/" + laneId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message", containsString("已")));
    }

    @Test
    @DisplayName("无效状态操作 → 400")
    void shouldRejectInvalidStatusAction() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "无效操作", "INV_01", "ENTRY");

        mockMvc.perform(post("/admin/lanes/" + laneId + "/status")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DELETE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("无效")));
    }

    // ==================== ⑤ 权限校验 ====================

    @Test
    @DisplayName("未登录访问 → 401")
    void shouldRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/admin/lanes?parkingLotId=" + parkingLotId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("无权限角色创建车道 → 403")
    void shouldRejectNoPermission() throws Exception {
        // 创建 booth_operator
        mockMvc.perform(post("/admin/employees")
                .header("Authorization", "Bearer " + customerAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"岗亭操作员\",\"phone\":\"13911110001\"," +
                        "\"password\":\"pass123\",\"roleCode\":\"booth_operator\"," +
                        "\"parkingLotIds\":[" + parkingLotId + "]}"));
        String boothToken = login("13911110001", "pass123");

        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + boothToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"越权车道\"," +
                                "\"code\":\"NO_PERM\",\"direction\":\"ENTRY\"}"))
                .andExpect(status().isForbidden());
    }

    // ==================== ⑥ 平台用户跨租户 ====================

    @Test
    @DisplayName("平台用户可查看任意租户车道")
    void shouldAllowPlatformUserToViewAnyLane() throws Exception {
        Long laneId = createLaneViaApi(customerAdminToken, parkingLotId, "平台可查车道", "PLAT_01", "ENTRY");

        mockMvc.perform(get("/admin/lanes/" + laneId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("平台可查车道"));
    }

    // ==================== ⑦ 自动放行策略 ====================

    @Test
    @DisplayName("指定自动放行策略 → 创建成功")
    void shouldCreateWithAutoReleasePolicy() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"自动放行车道\"," +
                                "\"code\":\"AUTO_01\",\"direction\":\"EXIT\",\"autoReleasePolicy\":\"AFTER_PAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.autoReleasePolicy").value("AFTER_PAY"));
    }

    @Test
    @DisplayName("非法自动放行策略 → 400")
    void shouldRejectInvalidAutoReleasePolicy() throws Exception {
        mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + customerAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkingLotId\":" + parkingLotId + ",\"name\":\"非法策略\"," +
                                "\"code\":\"BAD_POL\",\"direction\":\"ENTRY\",\"autoReleasePolicy\":\"WEIRD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("无效的自动放行策略")));
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

    private Long createLaneViaApi(String token, Long parkingLotId, String name, String code, String direction) throws Exception {
        String resp = mockMvc.perform(post("/admin/lanes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"parkingLotId\":%d,\"name\":\"%s\",\"code\":\"%s\",\"direction\":\"%s\"}",
                                parkingLotId, name, code, direction)))
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

    private void cleanupTestData() {
        laneMapper.delete(new LambdaQueryWrapper<>());
        parkingLotMapper.delete(new LambdaQueryWrapper<>());
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().ne(SysUser::getUsername, "admin"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>().isNotNull(Tenant::getContactPhone));
    }
}
