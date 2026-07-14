package com.jushan.boot.sprint1;

import com.jushan.boot.test.TestcontainersBaseTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint 1 集成测试基类。
 * <p>
 * 提供 Testcontainers MySQL + Redis、REST Assured 配置、登录工具与通用断言。
 * 所有 Sprint 1 集成测试均继承此类，确保每个测试类独立且可单独运行。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class Sprint1IntegrationTest extends TestcontainersBaseTest {

    /** Redis 7 容器镜像 */
    static final String REDIS_IMAGE = "redis:7-alpine";

    /**
     * 共享 Redis 容器实例。
     */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    /**
     * 将 Redis 容器连接信息注册到 Spring Environment。
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /** 本地随机端口 */
    @LocalServerPort
    protected int port;

    /** Redis 操作模板 */
    @Autowired
    protected StringRedisTemplate redisTemplate;

    /** JDBC 操作模板（绕过 MyBatis-Plus 租户拦截器，用于清理与断言） */
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 配置 REST Assured 使用当前随机端口。
     */
    @BeforeAll
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * 登录指定账号并返回 JWT Token。
     *
     * @param username 用户名
     * @param password 密码
     * @return JWT Token
     */
    protected String loginAs(String username, String password) {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .post("/api/v1/auth/login");
        response.then().statusCode(200);
        return response.jsonPath().getString("data.token");
    }

    /**
     * 创建已携带 Authorization Bearer Token 的 REST Assured 请求规范。
     *
     * @param token JWT Token
     * @return RequestSpecification
     */
    protected RequestSpecification givenWithToken(String token) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON);
    }

    /**
     * 断言 Redis 中存在指定用户的权限缓存。
     *
     * @param userId 用户 ID
     */
    protected void assertPermissionCacheExists(Long userId) {
        String key = "auth:permissions:" + userId;
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertTrue(
                        Boolean.TRUE.equals(redisTemplate.hasKey(key)),
                        () -> "权限缓存应存在: " + key));
    }

    /**
     * 断言 Redis 中不存在指定用户的权限缓存。
     *
     * @param userId 用户 ID
     */
    protected void assertPermissionCacheAbsent(Long userId) {
        String key = "auth:permissions:" + userId;
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertTrue(
                        Boolean.FALSE.equals(redisTemplate.hasKey(key)),
                        () -> "权限缓存应已清除: " + key));
    }

    /**
     * 生成带类名前缀的唯一标识，避免跨类软删除唯一约束冲突。
     *
     * @param clazz 测试类
     * @return 唯一前缀
     */
    protected String uniquePrefix(Class<?> clazz) {
        return clazz.getSimpleName() + "_" + System.nanoTime();
    }

    // ==================== 数据创建辅助方法 ====================

    /**
     * 以指定 token 创建公司，返回公司 ID。
     *
     * @param token   JWT Token
     * @param name    公司名称
     * @param code    公司编码
     * @param level   公司级别
     * @param tenantId 所属租户 ID（用于断言，实际从 token 推导）
     * @return 公司 ID
     */
    protected Long createCompany(String token, String name, String code, int level, Long tenantId) {
        Response response = givenWithToken(token)
                .body(Map.of(
                        "parentId", 0,
                        "name", name,
                        "code", code,
                        "level", level,
                        "contactName", "联系人",
                        "contactPhone", "13800138000",
                        "address", "测试地址",
                        "sortOrder", 0))
                .post("/api/v1/companies");
        response.then().statusCode(200);
        return response.jsonPath().getLong("data.id");
    }

    /**
     * 创建管理员账号，返回账号 ID。
     *
     * @param token     JWT Token
     * @param username  登录账号
     * @param password  初始密码
     * @param level     管理员级别
     * @param tenantId  租户 ID
     * @param companyId 公司 ID
     * @param lotId     停车场 ID（三级必填）
     * @param roleIds   角色 ID 列表
     * @return 账号 ID
     */
    protected Long createAdminAccount(String token, String username, String password,
                                      int level, Long tenantId, Long companyId, Long lotId,
                                      List<Long> roleIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("realName", "测试管理员");
        body.put("phone", "13800138001");
        body.put("email", "test@example.com");
        body.put("level", level);
        if (tenantId != null) {
            body.put("tenantId", tenantId);
        }
        if (companyId != null) {
            body.put("companyId", companyId);
        }
        if (lotId != null) {
            body.put("lotId", lotId);
        }
        body.put("status", 1);
        body.put("roleIds", roleIds);

        Response response = givenWithToken(token)
                .body(body)
                .post("/api/v1/admin-accounts");
        response.then().statusCode(200);
        return response.jsonPath().getLong("data.id");
    }

    /**
     * 创建自定义角色，返回角色 ID。
     *
     * @param token    JWT Token
     * @param roleName 角色名称
     * @param roleCode 角色编码
     * @param tenantId 租户 ID
     * @return 角色 ID
     */
    protected Long createCustomRole(String token, String roleName, String roleCode, Long tenantId) {
        Map<String, Object> body = new HashMap<>();
        body.put("roleName", roleName);
        body.put("roleCode", roleCode);
        body.put("description", "测试角色");
        if (tenantId != null) {
            body.put("tenantId", tenantId);
        }
        Response response = givenWithToken(token)
                .body(body)
                .post("/api/v1/custom-roles");
        response.then().statusCode(200);
        return response.jsonPath().getLong("data.id");
    }

    /**
     * 保存角色权限矩阵。
     *
     * @param token       JWT Token
     * @param roleId      角色 ID
     * @param permissions 权限项列表，每项为 {permissionCode, permissionType, dataScope}
     */
    protected void saveRolePermissions(String token, Long roleId,
                                       List<Map<String, String>> permissions) {
        givenWithToken(token)
                .body(Map.of("permissions", permissions))
                .put("/api/v1/custom-roles/" + roleId + "/permissions")
                .then()
                .statusCode(200);
    }

    // ==================== 数据库直接操作辅助方法 ====================

    /**
     * 直接插入租户，返回自增 ID。
     *
     * @param name 租户名称
     * @param code 租户编码
     * @return 租户 ID
     */
    protected Long insertTenant(String name, String code) {
        jdbcTemplate.update(
                "INSERT INTO sys_tenant (name, code, status) VALUES (?, ?, 1)",
                name, code);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * 直接插入公司，返回自增 ID。
     *
     * @param tenantId 租户 ID
     * @param name     公司名称
     * @param level    公司级别
     * @param parentId 上级公司 ID
     * @return 公司 ID
     */
    protected Long insertCompany(Long tenantId, String name, int level, Long parentId) {
        jdbcTemplate.update(
                "INSERT INTO sys_company (tenant_id, parent_id, name, level, contact_name, contact_phone, address, sort_order, is_deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId, parentId != null ? parentId : 0L, name, level, "联系人", "13800138000", "测试地址", 0, 0);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * 直接插入停车场，返回自增 ID。
     *
     * @param tenantId 租户 ID
     * @param name     停车场名称
     * @return 停车场 ID
     */
    protected Long insertParkingLot(Long tenantId, String name) {
        jdbcTemplate.update(
                "INSERT INTO parking_lot (tenant_id, name, status) VALUES (?, ?, 'ENABLED')",
                tenantId, name);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
