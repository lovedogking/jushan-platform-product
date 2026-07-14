package com.jushan.boot.sprint1;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员账号安全集成测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>连续 5 次登录失败后账号锁定 30 分钟</li>
 *   <li>重置密码后旧密码失效</li>
 *   <li>密码以 BCrypt 哈希存储，数据库无明文</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("管理员账号安全集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAccountSecurityTest extends Sprint1IntegrationTest {

    private static final String ORIGINAL_PASSWORD = "Admin@123";
    private static final String WRONG_PASSWORD = "WrongPass";
    private static final Long TENANT_ID = 1L;

    private static String superAdminToken;
    private static Long companyId;
    private static Long lockoutAdminId;
    private static Long resetPasswordAdminId;

    private static String prefix;

    @BeforeAll
    void setUp() {
        prefix = uniquePrefix(AdminAccountSecurityTest.class);

        // Given: 超级管理员登录
        superAdminToken = loginAs("super_admin", "admin123");

        // Given: 准备公司
        companyId = insertCompany(TENANT_ID, prefix + "公司", 1, 0L);

        // Given: 创建用于锁定测试的二级管理员
        lockoutAdminId = createAdminAccount(superAdminToken, prefix + "lockout", ORIGINAL_PASSWORD,
                2, TENANT_ID, companyId, null, List.of(1L));

        // Given: 创建用于重置密码测试的二级管理员
        resetPasswordAdminId = createAdminAccount(superAdminToken, prefix + "reset", ORIGINAL_PASSWORD,
                2, TENANT_ID, companyId, null, List.of(1L));
    }

    @AfterAll
    void tearDown() {
        // 清理账号
        for (Long id : List.of(lockoutAdminId, resetPasswordAdminId)) {
            if (id != null) {
                jdbcTemplate.update("DELETE FROM sys_admin_account_role WHERE admin_account_id = ?", id);
                jdbcTemplate.update("DELETE FROM sys_admin_account WHERE id = ?", id);
                redisTemplate.delete("auth:permissions:" + id);
            }
        }
        // 清理公司
        if (companyId != null) {
            jdbcTemplate.update("DELETE FROM sys_company WHERE id = ?", companyId);
        }
        // 确保超级管理员未被锁定/禁用
        jdbcTemplate.update(
                "UPDATE sys_admin_account SET status = 1, login_fail_count = 0, lock_until = NULL WHERE username = 'super_admin'");
        redisTemplate.delete("auth:permissions:1");
    }

    @Test
    @DisplayName("连续 5 次登录失败后账号锁定 30 分钟")
    void fiveFailedLoginsLockAccount() {
        String username = prefix + "lockout";

        // Given-When: 连续 5 次使用错误密码登录
        for (int i = 0; i < 5; i++) {
            given()
                    .contentType(io.restassured.http.ContentType.JSON)
                    .body(Map.of("username", username, "password", WRONG_PASSWORD))
                    .post("/api/v1/auth/login")
                    .then()
                    .statusCode(401);
        }

        // Then: 数据库中账号已锁定
        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT status, login_fail_count, lock_until FROM sys_admin_account WHERE id = ?", lockoutAdminId);
        assertEquals(2, account.get("status"), "账号状态应为锁定");
        assertEquals(5, account.get("login_fail_count"), "登录失败次数应为 5");
        assertNotNull(account.get("lock_until"), "锁定截止时间应不为空");
        assertTrue(((LocalDateTime) account.get("lock_until")).isAfter(LocalDateTime.now()),
                "锁定截止时间应在未来");

        // When: 第 6 次使用正确密码登录
        given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(Map.of("username", username, "password", ORIGINAL_PASSWORD))
                .post("/api/v1/auth/login")
                .then()
                // Then: 仍处于锁定状态
                .statusCode(403)
                .body("code", equalTo(403))
                .body("message", containsString("锁定"));
    }

    @Test
    @DisplayName("锁定期过后自动解锁")
    void lockedAccountUnlocksAutomatically() {
        String username = prefix + "lockout";

        // Given: 手动将锁定时间设置为过去
        jdbcTemplate.update(
                "UPDATE sys_admin_account SET status = 2, login_fail_count = 5, lock_until = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), lockoutAdminId);

        // When: 使用正确密码登录
        given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(Map.of("username", username, "password", ORIGINAL_PASSWORD))
                .post("/api/v1/auth/login")
                .then()
                // Then: 登录成功
                .statusCode(200)
                .body("code", equalTo(0));

        // Then: 数据库状态已重置
        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT status, login_fail_count, lock_until FROM sys_admin_account WHERE id = ?", lockoutAdminId);
        assertEquals(1, account.get("status"));
        assertEquals(0, account.get("login_fail_count"));
        assertEquals(null, account.get("lock_until"));
    }

    @Test
    @DisplayName("禁用账号无法登录")
    void disabledAccountCannotLogin() {
        String username = prefix + "lockout";

        // Given: 禁用账号
        jdbcTemplate.update(
                "UPDATE sys_admin_account SET status = 0, login_fail_count = 0, lock_until = NULL WHERE id = ?",
                lockoutAdminId);

        // When: 尝试登录
        given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(Map.of("username", username, "password", ORIGINAL_PASSWORD))
                .post("/api/v1/auth/login")
                .then()
                // Then: 返回禁用提示
                .statusCode(401)
                .body("code", equalTo(401))
                .body("message", containsString("禁用"));

        // 恢复启用，避免影响后续清理
        jdbcTemplate.update(
                "UPDATE sys_admin_account SET status = 1 WHERE id = ?", lockoutAdminId);
    }

    @Test
    @DisplayName("重置密码后旧密码失效")
    void resetPasswordProducesValidPassword() {
        // When: 超级管理员重置密码
        Response response = givenWithToken(superAdminToken)
                .post("/api/v1/admin-accounts/" + resetPasswordAdminId + "/reset-password");
        response.then()
                .statusCode(200)
                .body("code", equalTo(0));
        String newPassword = response.jsonPath().getString("data.plainPassword");
        assertNotNull(newPassword);
        assertNotEquals(ORIGINAL_PASSWORD, newPassword);

        // Then: 旧密码无法登录
        given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(Map.of("username", prefix + "reset", "password", ORIGINAL_PASSWORD))
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);

        // Then: 新密码可以登录
        given()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(Map.of("username", prefix + "reset", "password", newPassword))
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    @DisplayName("二级管理员缺少公司绑定被拒绝")
    void invalidLevelBindingRejected() {
        // When: 创建二级管理员但不传 companyId
        givenWithToken(superAdminToken)
                .body(Map.of(
                        "username", prefix + "no_company",
                        "password", ORIGINAL_PASSWORD,
                        "realName", "测试",
                        "level", 2,
                        "tenantId", TENANT_ID,
                        "status", 1))
                .post("/api/v1/admin-accounts")
                .then()
                // Then: 参数校验或业务校验失败
                .statusCode(400);
    }

    @Test
    @DisplayName("弱密码被校验拒绝")
    void weakPasswordRejected() {
        // When: 使用 3 位密码创建账号
        givenWithToken(superAdminToken)
                .body(Map.of(
                        "username", prefix + "weak",
                        "password", "123",
                        "realName", "测试",
                        "level", 2,
                        "tenantId", TENANT_ID,
                        "companyId", companyId,
                        "status", 1))
                .post("/api/v1/admin-accounts")
                .then()
                // Then: 校验失败返回 400
                .statusCode(400);
    }

    @Test
    @DisplayName("数据库中密码为 BCrypt 哈希，无明文")
    void databasePasswordIsBcryptHash() {
        // When: 直接查询数据库密码字段
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password FROM sys_admin_account WHERE id = ?", String.class, lockoutAdminId);

        // Then: 应为 BCrypt 哈希格式
        assertNotNull(passwordHash);
        assertTrue(passwordHash.startsWith("$2a$") || passwordHash.startsWith("$2b$"),
                "密码应为 BCrypt 哈希");
        assertNotEquals(ORIGINAL_PASSWORD, passwordHash, "数据库不应存储明文密码");
    }
}
