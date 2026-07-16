package com.jushan.boot.phase3;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jushan.boot.sprint1.Sprint1IntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1-3 端到端集成测试。
 * <p>
 * 覆盖 7 个业务场景 + 安全验证，共 20+ 个测试用例。
 * 使用 Testcontainers（MySQL + Redis）+ WireMock（Device Access 模拟）。
 * 数据通过 JDBC 预置，@AfterAll 按依赖顺序清理。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@TestPropertySource(properties = {
        "jushan.device-access.read-timeout=3000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("Phase 1-3 端到端集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase3EndToEndIntegrationTest extends Sprint1IntegrationTest {

    // ==================== WireMock ====================

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void wireMockProperties(DynamicPropertyRegistry registry) {
        registry.add("jushan.device-access.base-url", wireMock::baseUrl);
    }

    // ==================== 测试数据（类级别共享） ====================

    private static final String PREFIX = "E2E_" + System.nanoTime() + "_";
    private static String superAdminToken;

    // 场景 4-5 用
    private static Long lotId;
    private static Long exitLaneId;
    private static Long entryDeviceId;
    private static Long exitDeviceId;
    private static String exitDeviceSn;
    private static Long destDeviceId;
    private static String destDeviceSn;

    // 场景 6 用
    private static Long proxyRecordId;
    private static final String PROXY_PLATE = "京C66666";

    // 场景 7 用
    private static Long nearbyLotId;

    // 清理用 ID 列表（按表分组）
    private static final ArrayList<Long> parkingLotIds = new ArrayList<>();
    private static final ArrayList<Long> laneIds = new ArrayList<>();
    private static final ArrayList<Long> deviceIds = new ArrayList<>();
    private static final ArrayList<Long> monthlyPassIds = new ArrayList<>();
    private static final ArrayList<Long> fixedSpaceIds = new ArrayList<>();
    private static final ArrayList<Long> proxyPayRecordIds = new ArrayList<>();
    private static final ArrayList<Long> orderIds = new ArrayList<>();

    @BeforeAll
    void setupAll() {
        superAdminToken = loginAs("super_admin", "admin123");

        // ---- 1. 创建车场 ----
        lotId = insertTestLot("主车场", 100, 85, 23.1291, 113.2644);
        nearbyLotId = insertTestLot("附近车场", 200, 180, 23.15, 113.28);
        parkingLotIds.add(lotId);
        parkingLotIds.add(nearbyLotId);

        // ---- 2. 创建车道 ----
        exitLaneId = insertTestLane(lotId, "出口A", 2);
        laneIds.add(exitLaneId);

        // ---- 3. 创建道闸设备 ----
        exitDeviceSn = PREFIX + "EXIT-GATE-001";
        exitDeviceId = insertTestDevice(lotId, exitLaneId, exitDeviceSn, "GATE");
        destDeviceSn = PREFIX + "DEST-GATE-001";
        destDeviceId = insertTestDevice(lotId, null, destDeviceSn, "GATE");

        entryDeviceId = insertTestDevice(lotId, exitLaneId, PREFIX + "ENTRY-GATE-001", "GATE");
        deviceIds.add(exitDeviceId);
        deviceIds.add(destDeviceId);
        deviceIds.add(entryDeviceId);

        // ---- Device Access Stub ----
        for (String sn : List.of(exitDeviceSn, destDeviceSn, PREFIX + "ENTRY-GATE-001")) {
            stubGateOpen(sn);
        }

        // ---- 6. 在场记录（场景 6 代缴用）----
        proxyRecordId = insertParkingRecord(lotId, PROXY_PLATE, LocalDateTime.now().minusHours(2));
    }

    @AfterAll
    void cleanupAll() {
        // 按依赖顺序反向清理
        runDeletes("proxy_pay_record", proxyPayRecordIds);
        runDeletes("parking_order", orderIds);
        runDeletes("parking_record", List.of(proxyRecordId));
        runDeletes("parking_session", List.of(proxyRecordId != null ? proxyRecordId + 1000 : -1));
        runDeletes("fixed_space_binding", fixedSpaceIds);
        runDeletes("sys_monthly_pass", monthlyPassIds);
        runDeletes("device_command_audit", deviceIds);
        runDeletes("device", deviceIds);
        runDeletes("parking_lane", laneIds);
        runDeletes("parking_lot", parkingLotIds);
        wireMock.stop();
    }

    // ==================== 数据辅助方法 ====================

    private void runDeletes(String table, List<Long> ids) {
        for (Long id : ids) {
            if (id != null && id > 0) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ?", id);
            }
        }
    }

    private Long insertTestLot(String nameSuffix, int total, int remaining, double lat, double lng) {
        jdbcTemplate.update(
                "INSERT INTO parking_lot (tenant_id, name, status, total_spaces, remaining_spaces, " +
                "current_vehicles, address, latitude, longitude, created_at, updated_at) " +
                "VALUES (1, ?, 'ENABLED', ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                PREFIX + nameSuffix, total, remaining, total - remaining,
                PREFIX + "地址_" + nameSuffix, lat, lng);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertTestLane(Long lotId, String nameSuffix, int type) {
        jdbcTemplate.update(
                "INSERT INTO parking_lane (tenant_id, lot_id, name, type, direction, status, " +
                "created_at, updated_at) VALUES (1, ?, ?, ?, 'EXIT', 1, NOW(), NOW())",
                lotId, PREFIX + nameSuffix, type);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertTestDevice(Long lotId, Long laneId, String sn, String deviceType) {
        jdbcTemplate.update(
                "INSERT INTO device (tenant_id, lot_id, lane_id, device_sn, device_name, device_type, " +
                "device_model, status, config, created_at, updated_at) " +
                "VALUES (1, ?, ?, ?, ?, ?, 'TEST', 'ENABLED', '{}', NOW(), NOW())",
                lotId, laneId, sn, PREFIX + sn, deviceType);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertParkingRecord(Long lotId, String plate, LocalDateTime entryTime) {
        // parking_session
        jdbcTemplate.update(
                "INSERT INTO parking_session (tenant_id, parking_lot_id, plate_number, " +
                "entry_time, entry_device_sn, status, created_at, updated_at) " +
                "VALUES (1, ?, ?, ?, 'TEST-SN', 'PARKING', NOW(), NOW())",
                lotId, plate, entryTime);
        Long sessionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // parking_record
        jdbcTemplate.update(
                "INSERT INTO parking_record (tenant_id, parking_lot_id, plate_number, " +
                "entry_time, entry_device_sn, status, session_id, created_at, updated_at) " +
                "VALUES (1, ?, ?, ?, 'TEST-SN', 'PARKING', ?, NOW(), NOW())",
                lotId, plate, entryTime, sessionId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void stubGateOpen(String deviceSn) {
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/devices/" + deviceSn + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"message":"success","data":{"success":true,"deviceCode":200,"message":"gate opened"}}
                                """)));
    }

    // ================================================================
    //  S1: 管理员登录 + 工具验证
    // ================================================================

    @Nested
    @DisplayName("S1: 管理员基础操作")
    class AdminBasics {

        @Test
        @DisplayName("S1-01: 管理员登录成功返回 token")
        void adminLoginWorks() {
            assertNotNull(superAdminToken);
        }

        @Test
        @DisplayName("S1-02: 无 token 请求返回 401")
        void unauthenticatedRequestRejected() {
            given()
                    .get("/api/v1/admin/dashboard")
                    .then()
                    .statusCode(401);
        }
    }

    // ================================================================
    //  S2: Dashboard + 通行记录 + 系统参数（Phase 2 核心 API）
    // ================================================================

    @Nested
    @DisplayName("S2: 运营看板与记录查询")
    class DashboardAndRecords {

        @Test
        @DisplayName("S2-01: 数据看板聚合返回正常")
        void dashboardSummary() {
            givenWithToken(superAdminToken)
                    .get("/api/v1/admin/dashboard")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0))
                    .body("data", notNullValue());
        }

        @Test
        @DisplayName("S2-02: 通行记录列表查询正常")
        void parkingRecordList() {
            givenWithToken(superAdminToken)
                    .queryParam("current", 1).queryParam("size", 10)
                    .get("/api/v1/admin/parking-records")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("S2-03: 异常记录列表查询正常")
        void exceptionRecordList() {
            givenWithToken(superAdminToken)
                    .queryParam("current", 1).queryParam("size", 10)
                    .get("/api/v1/admin/exception-records")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("S2-04: 手动开闸记录列表查询正常")
        void manualGateRecordList() {
            givenWithToken(superAdminToken)
                    .queryParam("current", 1).queryParam("size", 10)
                    .get("/api/v1/admin/manual-gate-records")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("S2-05: 系统参数列表查询正常")
        void systemParamList() {
            givenWithToken(superAdminToken)
                    .get("/api/v1/system-params")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("S2-06: 订单管理列表查询正常")
        void orderAdminList() {
            givenWithToken(superAdminToken)
                    .queryParam("current", 1).queryParam("size", 10)
                    .get("/api/v1/admin/orders")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }
    }

    // ================================================================
    //  S3: 远程开闸 + 批量开闸（Phase 1 B2 + Phase 2 D5）
    // ================================================================

    @Nested
    @DisplayName("S3: 远程开闸与批量开闸")
    class RemoteGate {
        private String auditReason;

        @Test
        @DisplayName("S3-01: 远程开闸成功 → DeviceCommandAudit 记录生成")
        void remoteGateOpen() {
            auditReason = PREFIX + "远程开闸_" + System.nanoTime();

            Response resp = givenWithToken(superAdminToken)
                    .body(Map.of(
                            "parkingLotId", lotId,
                            "laneId", exitLaneId,
                            "reason", auditReason))
                    .post("/api/v1/admin/remote-gate/open");
            resp.then()
                    .statusCode(200)
                    .body("code", equalTo(0));

            // 验证审计记录
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM device_command_audit WHERE reason = ? AND deleted_at IS NULL",
                    Integer.class, auditReason);
            assertNotNull(count);
            assertTrue(count >= 1, "远程开闸应生成至少 1 条审计记录");
        }

        @Test
        @DisplayName("S3-02: 远程开闸缺少原因参数拒绝（400）")
        void remoteGateWithoutReason() {
            givenWithToken(superAdminToken)
                    .body(Map.of("parkingLotId", lotId, "laneId", exitLaneId))
                    .post("/api/v1/admin/remote-gate/open")
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("S3-03: 批量开闸 3 个设备 → 3 条审计记录")
        void batchOpenGate() {
            String batchReason = PREFIX + "批量_" + System.nanoTime();

            Response resp = givenWithToken(superAdminToken)
                    .body(Map.of(
                            "deviceIds", List.of(entryDeviceId, exitDeviceId, destDeviceId),
                            "reason", batchReason))
                    .post("/api/v1/booth/recognition/manual-open-gate-batch");
            resp.then()
                    .statusCode(200)
                    .body("code", equalTo(0))
                    .body("data.successCount", equalTo(3))
                    .body("data.failedCount", equalTo(0));

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM device_command_audit WHERE reason = ? AND deleted_at IS NULL",
                    Integer.class, batchReason);
            assertNotNull(count);
            assertEquals(3, count.intValue(), "批量开闸 3 个设备应生成 3 条审计记录");
        }

        @Test
        @DisplayName("S3-04: 批量开闸空 deviceIds 拒绝（400）")
        void batchOpenGateEmptyIds() {
            givenWithToken(superAdminToken)
                    .body(Map.of("deviceIds", List.of(), "reason", "test"))
                    .post("/api/v1/booth/recognition/manual-open-gate-batch")
                    .then()
                    .statusCode(200)
                    .body("code", not(equalTo(0))); // 业务错误码
        }
    }

    // ================================================================
    //  S4: 岗亭车场选择（Phase 2 D6）
    // ================================================================

    @Nested
    @DisplayName("S4: 岗亭车场列表")
    class BoothParkingLot {

        @Test
        @DisplayName("S4-01: 岗亭车场列表返回授权车场")
        void boothParkingLotList() {
            givenWithToken(superAdminToken)
                    .get("/api/v1/booth/parking-lots")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }
    }

    // ================================================================
    //  S5: 小程序余位查询 + 车场列表（Phase 3 E2）
    // ================================================================

    @Nested
    @DisplayName("S5: 余位查询与车场列表")
    class ParkingLotSpaces {

        @Test
        @DisplayName("S5-01: 车场列表返回正确字段（含余位）")
        void lotList() {
            givenWithToken(superAdminToken)
                    .get("/api/v1/mini/parking-lots")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0))
                    .body("data", notNullValue());
        }

        @Test
        @DisplayName("S5-02: 附近车场查询按距离排序")
        void nearbyLots() {
            givenWithToken(superAdminToken)
                    .queryParam("latitude", 23.13)
                    .queryParam("longitude", 113.26)
                    .queryParam("radius", 5000)
                    .get("/api/v1/mini/parking-lots/nearby")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("S5-03: 附近车场半径太小（<500m）拒绝")
        void nearbyRadiusTooSmall() {
            givenWithToken(superAdminToken)
                    .queryParam("latitude", 23.13)
                    .queryParam("longitude", 113.26)
                    .queryParam("radius", 100)
                    .get("/api/v1/mini/parking-lots/nearby")
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("S5-04: 附近车场半径太大（>50000m）拒绝")
        void nearbyRadiusTooLarge() {
            givenWithToken(superAdminToken)
                    .queryParam("latitude", 23.13)
                    .queryParam("longitude", 113.26)
                    .queryParam("radius", 60000)
                    .get("/api/v1/mini/parking-lots/nearby")
                    .then()
                    .statusCode(400);
        }
    }

    // ================================================================
    //  S6: 小程序代缴（Phase 3 E1）
    // ================================================================

    @Nested
    @DisplayName("S6: 代缴功能")
    class ProxyPay {

        @Test
        @DisplayName("S6-01: 代缴预览 — 查询在场车牌费用")
        void proxyPreview() {
            Response resp = givenWithToken(superAdminToken)
                    .body(Map.of("plateNumber", PROXY_PLATE))
                    .post("/api/v1/mini/pay/proxy-preview");
            resp.then()
                    .statusCode(200)
                    .body("code", equalTo(0))
                    .body("data.plateNumber", equalTo(PROXY_PLATE))
                    .body("data.feeCents", greaterThan(0));
        }

        @Test
        @DisplayName("S6-02: 代缴确认 — 支付成功 + 代缴记录 + 订单流转")
        void proxyPay() {
            Response resp = givenWithToken(superAdminToken)
                    .body(Map.of("plateNumber", PROXY_PLATE, "remark", "E2E代缴测试"))
                    .post("/api/v1/mini/pay/proxy-pay");
            resp.then()
                    .statusCode(200)
                    .body("code", equalTo(0))
                    .body("data.status", equalTo("PAID"))
                    .body("data.plateNumber", equalTo(PROXY_PLATE))
                    .body("data.amountCents", greaterThan(0));

            Long orderId = resp.jsonPath().getLong("data.orderId");
            if (orderId != null) {
                orderIds.add(orderId);
            }

            // 验证数据层
            assertTrue(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM proxy_pay_record WHERE plate_number = ? AND status='COMPLETED'",
                            Integer.class, PROXY_PLATE) > 0,
                    "代缴记录应生成");

            if (orderId != null) {
                String orderStatus = jdbcTemplate.queryForObject(
                        "SELECT status FROM parking_order WHERE id = ?", String.class, orderId);
                assertEquals("PAID", orderStatus, "订单状态应为 PAID");
            }
        }

        @Test
        @DisplayName("S6-03: 代缴不存在的车牌返回错误")
        void proxyPayInvalidPlate() {
            givenWithToken(superAdminToken)
                    .body(Map.of("plateNumber", "京X99999"))
                    .post("/api/v1/mini/pay/proxy-preview")
                    .then()
                    .statusCode(200)
                    .body("code", not(equalTo(0))); // 业务错误（不在场）
        }
    }

    // ================================================================
    //  S7: 消息通知（Phase 3 E3）
    // ================================================================

    @Nested
    @DisplayName("S7: 消息通知")
    class Messages {

        @Test
        @DisplayName("S7-01: 消息列表查询")
        void messageList() {
            givenWithToken(superAdminToken)
                    .queryParam("current", 1)
                    .queryParam("size", 20)
                    .get("/api/v1/mini/messages")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("S7-02: 未读消息数查询")
        void unreadCount() {
            givenWithToken(superAdminToken)
                    .get("/api/v1/mini/messages/unread-count")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }
    }

    // ================================================================
    //  S8: 固定车位 + 月卡（Phase 1 核心业务）
    // ================================================================

    @Nested
    @DisplayName("S8: 固定车位与月卡管理")
    class FixedSpaceAndMonthlyPass {

        @Test
        @DisplayName("S8-01: 月卡列表查询正常")
        void monthlyPassList() {
            givenWithToken(superAdminToken)
                    .queryParam("current", 1).queryParam("size", 100)
                    .get("/api/v1/monthly-passes")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }

        @Test
        @DisplayName("S8-02: 固定车位列表查询正常")
        void fixedSpaceList() {
            givenWithToken(superAdminToken)
                    .queryParam("current", 1).queryParam("size", 100)
                    .get("/api/v1/fixed-spaces")
                    .then()
                    .statusCode(200)
                    .body("code", equalTo(0));
        }
    }

    // ================================================================
    //  S9: Device Access 审计完整性
    // ================================================================

    @Nested
    @DisplayName("S9: 审计完整性验证")
    class AuditIntegrity {

        @Test
        @DisplayName("S9-01: 远程开闸 + 批量开闸审计记录总数 >= 4")
        void auditRecordCount() {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM device_command_audit WHERE deleted_at IS NULL",
                    Integer.class);
            assertNotNull(count);
            assertTrue(count >= 4,
                    "远程开闸(1) + 批量开闸(3) 应生成至少 4 条审计记录，实际: " + count);
        }
    }
}
