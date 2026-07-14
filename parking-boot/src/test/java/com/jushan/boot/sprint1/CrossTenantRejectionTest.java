package com.jushan.boot.sprint1;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 跨租户拒绝集成测试。
 * <p>
 * 验证二级管理员无法通过修改请求参数或直接使用 URL 访问其他租户的数据，
 * 所有越权操作均被拒绝（HTTP 403）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("跨租户拒绝集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossTenantRejectionTest extends Sprint1IntegrationTest {

    private static final String PASSWORD = "Admin@123";
    private static final Long TENANT_ID_1 = 1L;

    private static String superAdminToken;
    private static String tenant1AdminToken;
    private static String tenant2AdminToken;

    private static Long tenant2Id;
    private static Long companyAId;
    private static Long companyBId;
    private static Long tenant1AdminId;
    private static Long tenant2AdminId;
    private static Long tenant1AccountId;

    private static String prefix;

    @BeforeAll
    void setUp() {
        prefix = uniquePrefix(CrossTenantRejectionTest.class);

        // Given: 超级管理员登录
        superAdminToken = loginAs("super_admin", "admin123");

        // Given: 创建租户 2
        tenant2Id = insertTenant(prefix + "租户2", prefix + "TENANT2");

        // Given: 为两个租户分别插入公司
        companyAId = insertCompany(TENANT_ID_1, prefix + "公司A", 1, 0L);
        companyBId = insertCompany(tenant2Id, prefix + "公司B", 1, 0L);

        // Given: 创建两个租户管理员
        tenant1AdminId = createAdminAccount(superAdminToken, prefix + "t1_admin", PASSWORD,
                2, TENANT_ID_1, companyAId, null, List.of(1L));
        tenant2AdminId = createAdminAccount(superAdminToken, prefix + "t2_admin", PASSWORD,
                2, tenant2Id, companyBId, null, List.of(1L));

        // When: 登录两个租户管理员
        tenant1AdminToken = loginAs(prefix + "t1_admin", PASSWORD);
        tenant2AdminToken = loginAs(prefix + "t2_admin", PASSWORD);

        // Given: 租户 1 下再创建一个账号，用于越权访问测试
        tenant1AccountId = createAdminAccount(tenant1AdminToken, prefix + "t1_account", PASSWORD,
                3, TENANT_ID_1, companyAId, null, List.of(2L));
    }

    @AfterAll
    void tearDown() {
        // 清理账号
        for (Long id : List.of(tenant1AccountId, tenant1AdminId, tenant2AdminId)) {
            if (id != null) {
                jdbcTemplate.update("DELETE FROM sys_admin_account_role WHERE admin_account_id = ?", id);
                jdbcTemplate.update("DELETE FROM sys_admin_account WHERE id = ?", id);
                redisTemplate.delete("auth:permissions:" + id);
            }
        }
        // 清理公司
        for (Long id : List.of(companyAId, companyBId)) {
            if (id != null) {
                jdbcTemplate.update("DELETE FROM sys_company WHERE id = ?", id);
            }
        }
        // 清理租户
        if (tenant2Id != null) {
            jdbcTemplate.update("DELETE FROM sys_tenant WHERE id = ?", tenant2Id);
        }
    }

    @Test
    @DisplayName("租户 B 管理员无法查看租户 A 公司详情")
    void tenantBCannotViewTenantACompany() {
        // When: 租户 B 管理员访问租户 A 公司
        givenWithToken(tenant2AdminToken)
                .get("/api/v1/companies/" + companyAId)
                .then()
                // Then: 被拒绝
                .statusCode(403)
                .body("code", equalTo(403));
    }

    @Test
    @DisplayName("租户 B 管理员无法编辑租户 A 公司")
    void tenantBCannotUpdateTenantACompany() {
        // When: 租户 B 管理员编辑租户 A 公司
        givenWithToken(tenant2AdminToken)
                .body(Map.of(
                        "parentId", 0,
                        "name", prefix + "hacked",
                        "code", prefix + "HACKED",
                        "level", 1,
                        "contactName", "联系人",
                        "contactPhone", "13800138000",
                        "address", "地址",
                        "sortOrder", 0))
                .put("/api/v1/companies/" + companyAId)
                .then()
                // Then: 被拒绝
                .statusCode(403)
                .body("code", equalTo(403));

        // Then: 数据库中公司名称未被篡改
        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM sys_company WHERE id = ?", String.class, companyAId);
        assertEquals(prefix + "公司A", name);
    }

    @Test
    @DisplayName("租户 B 管理员无法删除租户 A 公司")
    void tenantBCannotDeleteTenantACompany() {
        // When: 租户 B 管理员删除租户 A 公司
        givenWithToken(tenant2AdminToken)
                .delete("/api/v1/companies/" + companyAId)
                .then()
                // Then: 被拒绝
                .statusCode(403)
                .body("code", equalTo(403));

        // Then: 公司仍存在
        Integer isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM sys_company WHERE id = ?", Integer.class, companyAId);
        assertEquals(0, isDeleted);
    }

    @Test
    @DisplayName("租户 B 管理员无法管理租户 A 的账号")
    void tenantBCannotManageTenantAAdminAccount() {
        // When: 查看详情
        givenWithToken(tenant2AdminToken)
                .get("/api/v1/admin-accounts/" + tenant1AccountId)
                .then()
                .statusCode(403)
                .body("code", equalTo(403));

        // When: 重置密码
        givenWithToken(tenant2AdminToken)
                .post("/api/v1/admin-accounts/" + tenant1AccountId + "/reset-password")
                .then()
                .statusCode(403)
                .body("code", equalTo(403));
    }

    @Test
    @DisplayName("修改 tenant_id 请求参数无法绕过租户隔离")
    void modifyingTenantIdParameterDoesNotBypassIsolation() {
        // When: 租户 B 管理员在查询参数中传入租户 A 的 tenant_id
        givenWithToken(tenant2AdminToken)
                .queryParam("current", 1)
                .queryParam("size", 100)
                .queryParam("tenantId", TENANT_ID_1)
                .get("/api/v1/companies")
                .then()
                // Then: 仍然只能看到租户 B 的数据
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.records", org.hamcrest.Matchers.hasSize(1))
                .body("data.records[0].id", equalTo(companyBId.intValue()));
    }
}
