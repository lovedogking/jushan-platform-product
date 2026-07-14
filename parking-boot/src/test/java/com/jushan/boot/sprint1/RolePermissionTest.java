package com.jushan.boot.sprint1;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色权限集成测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>@RequirePermission 注解生效，无权限接口返回 403</li>
 *   <li>权限矩阵保存后 Redis 缓存生效</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("角色权限集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RolePermissionTest extends Sprint1IntegrationTest {

    private static final String PASSWORD = "Admin@123";
    private static final Long TENANT_ID = 1L;

    private static String superAdminToken;
    private static String companyAdminToken;
    private static String viewerToken;

    private static Long companyId;
    private static Long companyAdminId;
    private static Long viewerAdminId;
    private static Long viewerRoleId;
    private static Long createdCompanyId;

    private static String prefix;

    @BeforeAll
    void setUp() {
        prefix = uniquePrefix(RolePermissionTest.class);

        // Given: 超级管理员登录
        superAdminToken = loginAs("super_admin", "admin123");

        // Given: 为租户 1 准备公司
        companyId = insertCompany(TENANT_ID, prefix + "公司", 1, 0L);

        // Given: 创建拥有完整公司权限的公司管理员（使用种子角色 COMPANY_ADMIN id=1）
        companyAdminId = createAdminAccount(superAdminToken, prefix + "company_admin", PASSWORD,
                2, TENANT_ID, companyId, null, List.of(1L));

        // Given: 创建仅拥有 company:view 权限的自定义角色
        viewerRoleId = createCustomRole(superAdminToken, prefix + "仅查看角色", prefix + "VIEWER", TENANT_ID);
        saveRolePermissions(superAdminToken, viewerRoleId, List.of(
                Map.of("permissionCode", "company:view", "permissionType", "menu", "dataScope", "company")
        ));

        // Given: 创建仅查看管理员并绑定自定义角色
        viewerAdminId = createAdminAccount(superAdminToken, prefix + "viewer", PASSWORD,
                2, TENANT_ID, companyId, null, List.of(viewerRoleId));

        // When: 登录两个租户管理员
        companyAdminToken = loginAs(prefix + "company_admin", PASSWORD);
        viewerToken = loginAs(prefix + "viewer", PASSWORD);
    }

    @AfterAll
    void tearDown() {
        if (createdCompanyId != null) {
            jdbcTemplate.update("DELETE FROM sys_company WHERE id = ?", createdCompanyId);
        }
        if (companyId != null) {
            jdbcTemplate.update("DELETE FROM sys_company WHERE id = ?", companyId);
        }
        for (Long id : List.of(companyAdminId, viewerAdminId)) {
            if (id != null) {
                jdbcTemplate.update("DELETE FROM sys_admin_account_role WHERE admin_account_id = ?", id);
                jdbcTemplate.update("DELETE FROM sys_admin_account WHERE id = ?", id);
                redisTemplate.delete("auth:permissions:" + id);
            }
        }
        if (viewerRoleId != null) {
            jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", viewerRoleId);
            jdbcTemplate.update("DELETE FROM sys_custom_role WHERE id = ?", viewerRoleId);
        }
    }

    @Test
    @DisplayName("超级管理员可创建公司")
    void superAdminCanCreateCompany() {
        // When: 超级管理员创建公司
        givenWithToken(superAdminToken)
                .body(Map.of(
                        "parentId", 0,
                        "name", prefix + "super_company",
                        "code", prefix + "SUPER",
                        "level", 1,
                        "contactName", "联系人",
                        "contactPhone", "13800138000",
                        "address", "地址",
                        "sortOrder", 0))
                .post("/api/v1/companies")
                .then()
                // Then: 创建成功
                .statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    @DisplayName("公司管理员可创建和编辑公司")
    void companyAdminCanCreateAndUpdateCompany() {
        // When: 公司管理员创建公司
        Response createResp = givenWithToken(companyAdminToken)
                .body(Map.of(
                        "parentId", 0,
                        "name", prefix + "create_company",
                        "code", prefix + "CREATE",
                        "level", 1,
                        "contactName", "联系人",
                        "contactPhone", "13800138000",
                        "address", "地址",
                        "sortOrder", 0))
                .post("/api/v1/companies");
        createResp.then().statusCode(200).body("code", equalTo(0));
        createdCompanyId = createResp.jsonPath().getLong("data.id");

        // When: 编辑公司
        givenWithToken(companyAdminToken)
                .body(Map.of(
                        "parentId", 0,
                        "name", prefix + "updated_company",
                        "code", prefix + "CREATE",
                        "level", 1,
                        "contactName", "联系人",
                        "contactPhone", "13800138000",
                        "address", "地址",
                        "sortOrder", 0))
                .put("/api/v1/companies/" + createdCompanyId)
                .then()
                // Then: 编辑成功
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.name", equalTo(prefix + "updated_company"));
    }

    @Test
    @DisplayName("仅查看角色无法创建公司")
    void viewerCannotCreateCompany() {
        // When: 仅查看管理员尝试创建公司
        givenWithToken(viewerToken)
                .body(Map.of(
                        "parentId", 0,
                        "name", prefix + "viewer_company",
                        "code", prefix + "VIEWER",
                        "level", 1,
                        "contactName", "联系人",
                        "contactPhone", "13800138000",
                        "address", "地址",
                        "sortOrder", 0))
                .post("/api/v1/companies")
                .then()
                // Then: 返回 403
                .statusCode(403)
                .body("code", equalTo(403));
    }

    @Test
    @DisplayName("仅查看角色无法删除公司")
    void viewerCannotDeleteCompany() {
        // When: 仅查看管理员尝试删除公司
        givenWithToken(viewerToken)
                .delete("/api/v1/companies/" + companyId)
                .then()
                // Then: 返回 403
                .statusCode(403)
                .body("code", equalTo(403));
    }

    @Test
    @DisplayName("权限缓存包含正确的权限编码")
    void permissionCacheContainsRequiredCodes() {
        // Given: 仅查看管理员已登录，权限应已缓存
        assertPermissionCacheExists(viewerAdminId);

        // When: 读取 Redis 权限集合
        String key = "auth:permissions:" + viewerAdminId;
        Set<String> permissions = redisTemplate.opsForSet().members(key);

        // Then: 只包含 company:view
        assertTrue(permissions.contains("company:view"), "应包含 company:view");
        assertFalse(permissions.contains("company:create"), "不应包含 company:create");
        assertFalse(permissions.contains("company:delete"), "不应包含 company:delete");
    }

    @Test
    @DisplayName("缺少 account:view 权限时访问账号列表返回 403")
    void missingPermissionReturns403() {
        // When: 仅查看管理员访问账号列表
        givenWithToken(viewerToken)
                .queryParam("page", 1)
                .queryParam("size", 20)
                .get("/api/v1/admin-accounts")
                .then()
                // Then: 返回 403
                .statusCode(403)
                .body("code", equalTo(403));
    }
}
