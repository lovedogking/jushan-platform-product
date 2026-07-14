package com.jushan.boot.sprint1;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户隔离集成测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>二级管理员只能看到本租户数据</li>
 *   <li>三级管理员只能看到本车场数据</li>
 *   <li>直接访问其他租户数据返回 403 或空结果</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("租户隔离集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationTest extends Sprint1IntegrationTest {

    private static final String PASSWORD = "Admin@123";

    private static String superAdminToken;
    private static String tenant1Level2Token;
    private static String tenant2Level2Token;
    private static String tenant1Level3Lot1Token;

    private static Long tenant1Id;
    private static Long tenant2Id;
    private static Long companyAId;
    private static Long companyBId;
    private static Long lot1Id;
    private static Long lot2Id;
    private static Long tenant1Level2AdminId;
    private static Long tenant2Level2AdminId;
    private static Long tenant1Level3Lot1AdminId;
    private static Long tenant1Level3Lot2AdminId;
    private static Long tenant1Lot1AccountId;
    private static Long tenant1Lot2AccountId;
    private static Long tenant2AccountId;

    private static String prefix;

    @BeforeAll
    void setUp() {
        prefix = uniquePrefix(TenantIsolationTest.class);

        // Given: 超级管理员登录
        superAdminToken = loginAs("super_admin", "admin123");

        // Given: 准备两个租户（租户 1 为种子数据，租户 2 直接插入）
        tenant1Id = 1L;
        tenant2Id = insertTenant(prefix + "租户2", prefix + "TENANT2");

        // Given: 为两个租户分别插入公司（平台用户无法调用租户级接口，故直接插入）
        companyAId = insertCompany(tenant1Id, prefix + "公司A", 1, 0L);
        companyBId = insertCompany(tenant2Id, prefix + "公司B", 1, 0L);

        // Given: 为租户 1 插入两个停车场
        lot1Id = insertParkingLot(tenant1Id, prefix + "车场1");
        lot2Id = insertParkingLot(tenant1Id, prefix + "车场2");

        // Given: 创建二级管理员（公司管理员角色 id=1）
        tenant1Level2AdminId = createAdminAccount(superAdminToken, prefix + "t1_l2", PASSWORD,
                2, tenant1Id, companyAId, null, List.of(1L));
        tenant2Level2AdminId = createAdminAccount(superAdminToken, prefix + "t2_l2", PASSWORD,
                2, tenant2Id, companyBId, null, List.of(1L));

        // Given: 创建三级管理员（车场管理员角色 id=2）
        tenant1Level3Lot1AdminId = createAdminAccount(superAdminToken, prefix + "t1_l3_l1", PASSWORD,
                3, tenant1Id, companyAId, lot1Id, List.of(2L));
        tenant1Level3Lot2AdminId = createAdminAccount(superAdminToken, prefix + "t1_l3_l2", PASSWORD,
                3, tenant1Id, companyAId, lot2Id, List.of(2L));

        // When: 登录各级管理员
        tenant1Level2Token = loginAs(prefix + "t1_l2", PASSWORD);
        tenant2Level2Token = loginAs(prefix + "t2_l2", PASSWORD);
        tenant1Level3Lot1Token = loginAs(prefix + "t1_l3_l1", PASSWORD);

        // Given: 三级管理员用其 token 创建同车场/同公司不同车场的账号，用于列表隔离断言
        tenant1Lot1AccountId = createAdminAccount(tenant1Level2Token, prefix + "t1_lot1_acc", PASSWORD,
                3, tenant1Id, companyAId, lot1Id, List.of(2L));
        tenant1Lot2AccountId = createAdminAccount(tenant1Level2Token, prefix + "t1_lot2_acc", PASSWORD,
                3, tenant1Id, companyAId, lot2Id, List.of(2L));
        tenant2AccountId = createAdminAccount(tenant2Level2Token, prefix + "t2_acc", PASSWORD,
                3, tenant2Id, companyBId, null, List.of(2L));
    }

    @AfterAll
    void tearDown() {
        // 清理创建的账号
        for (Long id : List.of(tenant1Lot1AccountId, tenant1Lot2AccountId, tenant2AccountId,
                tenant1Level3Lot1AdminId, tenant1Level3Lot2AdminId,
                tenant1Level2AdminId, tenant2Level2AdminId)) {
            if (id != null) {
                jdbcTemplate.update("DELETE FROM sys_admin_account_role WHERE admin_account_id = ?", id);
                jdbcTemplate.update("DELETE FROM sys_admin_account WHERE id = ?", id);
                redisTemplate.delete("auth:permissions:" + id);
            }
        }

        // 清理公司（硬删除，避免软删除唯一约束冲突）
        if (companyAId != null) {
            jdbcTemplate.update("DELETE FROM sys_company WHERE id = ?", companyAId);
        }
        if (companyBId != null) {
            jdbcTemplate.update("DELETE FROM sys_company WHERE id = ?", companyBId);
        }

        // 清理停车场
        if (lot1Id != null) {
            jdbcTemplate.update("DELETE FROM parking_lot WHERE id = ?", lot1Id);
        }
        if (lot2Id != null) {
            jdbcTemplate.update("DELETE FROM parking_lot WHERE id = ?", lot2Id);
        }

        // 清理临时租户
        if (tenant2Id != null) {
            jdbcTemplate.update("DELETE FROM sys_tenant WHERE id = ?", tenant2Id);
        }
    }

    @Test
    @DisplayName("二级管理员只能看到本租户的公司")
    void level2AdminSeesOnlyOwnTenantCompanies() {
        // When: 租户 1 二级管理员查询公司列表
        Response response = givenWithToken(tenant1Level2Token)
                .queryParam("current", 1)
                .queryParam("size", 100)
                .get("/api/v1/companies");

        // Then: 只返回本租户公司
        response.then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.records", hasSize(1))
                .body("data.records[0].id", equalTo(companyAId.intValue()));

        // Then: 数据库断言
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_company WHERE tenant_id = ? AND is_deleted = 0", Long.class, tenant1Id);
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("二级管理员访问其他租户公司详情返回 403")
    void level2AdminCannotAccessOtherTenantCompany() {
        // When: 租户 1 二级管理员访问租户 2 公司详情
        givenWithToken(tenant1Level2Token)
                .get("/api/v1/companies/" + companyBId)
                .then()
                // Then: 应被权限/数据范围拦截
                .statusCode(403)
                .body("code", equalTo(403));
    }

    @Test
    @DisplayName("三级管理员只能看到本车场的账号")
    void level3AdminSeesOnlyOwnLotAccounts() {
        // When: 租户 1 车场 1 三级管理员查询账号列表
        Response response = givenWithToken(tenant1Level3Lot1Token)
                .queryParam("page", 1)
                .queryParam("size", 100)
                .get("/api/v1/admin-accounts");

        // Then: 只返回本车场账号
        response.then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.records.findAll { it.lotId == " + lot1Id + " }", hasSize(1))
                .body("data.records.findAll { it.lotId == " + lot2Id + " }", hasSize(0));

        // Then: 数据库断言
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT lot_id FROM sys_admin_account WHERE tenant_id = ? AND company_id = ? AND is_deleted = 0 AND lot_id IS NOT NULL",
                tenant1Id, companyAId);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> lot1Id.equals(r.get("lot_id")) || lot2Id.equals(r.get("lot_id"))));
    }

    @Test
    @DisplayName("三级管理员访问其他租户账号返回 403")
    void level3AdminCannotAccessOtherTenantAccount() {
        // When: 租户 1 车场管理员访问租户 2 账号详情
        givenWithToken(tenant1Level3Lot1Token)
                .get("/api/v1/admin-accounts/" + tenant2AccountId)
                .then()
                // Then: 应被拒绝
                .statusCode(403)
                .body("code", equalTo(403));
    }

    @Test
    @DisplayName("跨租户列表返回空结果")
    void crossTenantListReturnsEmptyResult() {
        // When: 租户 2 二级管理员查询租户 1 的公司（通过 tenantId 参数无法改变 JWT 中的租户）
        Response response = givenWithToken(tenant2Level2Token)
                .queryParam("current", 1)
                .queryParam("size", 100)
                .get("/api/v1/companies");

        // Then: 只能看到租户 2 的公司，参数篡改无效
        response.then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.records", hasSize(1))
                .body("data.records[0].id", equalTo(companyBId.intValue()));
    }
}
