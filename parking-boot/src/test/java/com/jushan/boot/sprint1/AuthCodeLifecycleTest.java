package com.jushan.boot.sprint1;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 授权码生命周期集成测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>批量生成授权码格式正确</li>
 *   <li>过期/已用完/已禁用的码无法激活</li>
 *   <li>激活后 tenant_id 正确写入</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("授权码生命周期集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthCodeLifecycleTest extends Sprint1IntegrationTest {

    private static final String PASSWORD = "Admin@123";
    private static final Long TENANT_ID = 1L;

    private static String superAdminToken;
    private static String tenantAdminToken;

    private static Long companyId;
    private static Long tenantAdminId;
    private static String generatedCode;
    private static Long generatedCodeId;
    private static Long expiredCodeId;
    private static Long usedUpCodeId;

    private static String prefix;

    @BeforeAll
    void setUp() {
        prefix = uniquePrefix(AuthCodeLifecycleTest.class);

        // Given: 超级管理员登录
        superAdminToken = loginAs("super_admin", "admin123");

        // Given: 准备公司与租户管理员
        companyId = insertCompany(TENANT_ID, prefix + "公司", 1, 0L);
        tenantAdminId = createAdminAccount(superAdminToken, prefix + "tenant_admin", PASSWORD,
                2, TENANT_ID, companyId, null, List.of(1L));
        tenantAdminToken = loginAs(prefix + "tenant_admin", PASSWORD);
    }

    @AfterAll
    void tearDown() {
        // 清理授权码
        for (Long id : List.of(generatedCodeId, expiredCodeId, usedUpCodeId)) {
            if (id != null) {
                jdbcTemplate.update("DELETE FROM sys_auth_code WHERE id = ?", id);
            }
        }

        // 清理账号
        if (tenantAdminId != null) {
            jdbcTemplate.update("DELETE FROM sys_admin_account_role WHERE admin_account_id = ?", tenantAdminId);
            jdbcTemplate.update("DELETE FROM sys_admin_account WHERE id = ?", tenantAdminId);
            redisTemplate.delete("auth:permissions:" + tenantAdminId);
        }

        // 清理公司
        if (companyId != null) {
            jdbcTemplate.update("DELETE FROM sys_company WHERE id = ?", companyId);
        }
    }

    @Test
    @Order(1)
    @DisplayName("平台管理员批量生成授权码")
    void platformAdminGeneratesAuthCodes() {
        // When: 批量生成 3 条授权码
        Response response = givenWithToken(superAdminToken)
                .body(Map.of(
                        "count", 3,
                        "maxParkingCount", 5,
                        "validStart", LocalDate.now().minusDays(1).toString(),
                        "validEnd", LocalDate.now().plusDays(30).toString(),
                        "maxUseCount", 1,
                        "versionType", "STANDARD"))
                .post("/api/v1/auth-codes/batch");

        // Then: 返回 3 条格式正确的授权码
        response.then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data", hasSize(3));

        List<String> codes = response.jsonPath().getList("data.code", String.class);
        for (String code : codes) {
            assertNotNull(code);
            assertTrue(code.matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$"),
                    "授权码格式应为 XXXX-XXXX-XXXX-XXXX: " + code);
        }

        generatedCode = codes.get(0);
        generatedCodeId = response.jsonPath().getLong("data[0].id");

        // Then: 数据库状态为未使用
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM sys_auth_code WHERE id = ?", Integer.class, generatedCodeId);
        assertEquals(0, status);

        // Then: 租户 ID 为空
        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM sys_auth_code WHERE id = ?", Long.class, generatedCodeId);
        assertEquals(null, tenantId);
    }

    @Test
    @Order(2)
    @DisplayName("租户管理员激活授权码并写入 tenant_id")
    void tenantAdminActivatesAuthCode() {
        // Given: 先生成一条授权码
        Response genResp = givenWithToken(superAdminToken)
                .body(Map.of(
                        "count", 1,
                        "maxParkingCount", 3,
                        "validStart", LocalDate.now().minusDays(1).toString(),
                        "validEnd", LocalDate.now().plusDays(30).toString(),
                        "maxUseCount", 1,
                        "versionType", "BASIC"))
                .post("/api/v1/auth-codes/batch");
        genResp.then().statusCode(200).body("code", equalTo(0));
        generatedCode = genResp.jsonPath().getString("data[0].code");
        generatedCodeId = genResp.jsonPath().getLong("data[0].id");

        // When: 租户管理员激活
        Response activateResp = givenWithToken(tenantAdminToken)
                .post("/api/v1/auth-codes/" + generatedCode + "/activate");
        activateResp.then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo(1))
                .body("data.tenantId", equalTo(TENANT_ID.intValue()));

        // Then: 数据库中 tenant_id 与激活人正确
        Map<String, Object> record = jdbcTemplate.queryForMap(
                "SELECT tenant_id, status, activated_by FROM sys_auth_code WHERE id = ?", generatedCodeId);
        assertEquals(TENANT_ID, record.get("tenant_id"));
        assertEquals(1, record.get("status"));
        assertEquals(tenantAdminId, record.get("activated_by"));
    }

    @Test
    @Order(3)
    @DisplayName("租户只能看到已激活的授权码")
    void tenantSeesOnlyActivatedCodes() {
        // Given: 已执行激活测试，存在一条已激活码
        assertNotNull(generatedCodeId);

        // When: 租户管理员查询授权码列表
        Response response = givenWithToken(tenantAdminToken)
                .queryParam("page", 1)
                .queryParam("size", 100)
                .get("/api/v1/auth-codes");

        // Then: 只返回本租户已激活的码
        response.then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.records.findAll { it.tenantId == " + TENANT_ID + " }", hasSize(1));
    }

    @Test
    @Order(4)
    @DisplayName("平台管理员禁用授权码后无法激活")
    void disabledCodeCannotBeActivated() {
        // Given: 生成并禁用一条授权码
        Response genResp = givenWithToken(superAdminToken)
                .body(Map.of(
                        "count", 1,
                        "maxParkingCount", 1,
                        "validStart", LocalDate.now().minusDays(1).toString(),
                        "validEnd", LocalDate.now().plusDays(30).toString(),
                        "maxUseCount", 1,
                        "versionType", "BASIC"))
                .post("/api/v1/auth-codes/batch");
        genResp.then().statusCode(200).body("code", equalTo(0));
        Long disabledId = genResp.jsonPath().getLong("data[0].id");
        String disabledCode = genResp.jsonPath().getString("data[0].code");

        givenWithToken(superAdminToken)
                .put("/api/v1/auth-codes/" + disabledId + "/disable")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));

        // When: 尝试激活已禁用码
        givenWithToken(tenantAdminToken)
                .post("/api/v1/auth-codes/" + disabledCode + "/activate")
                .then()
                // Then: 返回业务错误
                .statusCode(400)
                .body("code", equalTo(400))
                .body("message", equalTo("授权码已被禁用"));

        // 记录待清理
        if (generatedCodeId == null) {
            generatedCodeId = disabledId;
        }
    }

    @Test
    @Order(5)
    @DisplayName("过期授权码无法激活")
    void expiredCodeCannotBeActivated() {
        // Given: 直接插入一条过期授权码
        String code = generateTestCode();
        jdbcTemplate.update(
                "INSERT INTO sys_auth_code (code, tenant_id, max_parking_count, valid_start, valid_end, used_count, max_use_count, version_type, status) " +
                        "VALUES (?, NULL, 1, ?, ?, 0, 1, 'BASIC', 0)",
                code, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        expiredCodeId = jdbcTemplate.queryForObject("SELECT id FROM sys_auth_code WHERE code = ?", Long.class, code);

        // When: 激活过期码
        givenWithToken(tenantAdminToken)
                .post("/api/v1/auth-codes/" + code + "/activate")
                .then()
                // Then: 返回过期错误
                .statusCode(400)
                .body("message", equalTo("授权码已过期"));
    }

    @Test
    @Order(6)
    @DisplayName("已达最大使用次数的授权码无法激活")
    void usedUpCodeCannotBeActivated() {
        // Given: 直接插入一条已用完的授权码
        String code = generateTestCode();
        jdbcTemplate.update(
                "INSERT INTO sys_auth_code (code, tenant_id, max_parking_count, valid_start, valid_end, used_count, max_use_count, version_type, status) " +
                        "VALUES (?, NULL, 1, ?, ?, 1, 1, 'BASIC', 0)",
                code, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        usedUpCodeId = jdbcTemplate.queryForObject("SELECT id FROM sys_auth_code WHERE code = ?", Long.class, code);

        // When: 激活已用完码
        givenWithToken(tenantAdminToken)
                .post("/api/v1/auth-codes/" + code + "/activate")
                .then()
                // Then: 返回使用次数错误
                .statusCode(400)
                .body("message", equalTo("授权码已达到最大使用次数"));
    }

    @Test
    @Order(7)
    @DisplayName("非平台管理员无法生成授权码")
    void nonPlatformUserCannotGenerate() {
        // When: 租户管理员尝试批量生成
        givenWithToken(tenantAdminToken)
                .body(Map.of(
                        "count", 1,
                        "maxParkingCount", 1,
                        "validStart", LocalDate.now().toString(),
                        "validEnd", LocalDate.now().plusDays(1).toString(),
                        "maxUseCount", 1,
                        "versionType", "BASIC"))
                .post("/api/v1/auth-codes/batch")
                .then()
                // Then: 返回 403
                .statusCode(403)
                .body("code", equalTo(403));
    }

    /**
     * 生成一个测试用的授权码字符串，确保长度不超过 50。
     */
    private String generateTestCode() {
        return "TST" + System.nanoTime();
    }
}
