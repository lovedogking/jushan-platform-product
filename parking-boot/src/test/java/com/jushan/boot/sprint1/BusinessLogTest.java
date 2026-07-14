package com.jushan.boot.sprint1;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务操作日志集成测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>@BusinessLog 触发后 sys_business_log 表有记录</li>
 *   <li>手机号/密码/邮箱/身份证字段已脱敏</li>
 *   <li>异步写入不阻塞接口（接口响应 &lt; 200ms）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Import(BusinessLogTestConfig.class)
@DisplayName("业务操作日志集成测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessLogTest extends Sprint1IntegrationTest {

    private static final Long SUPER_ADMIN_ID = 1L;
    private static final Long TENANT_ID = 1L;

    private static String superAdminToken;

    @BeforeAll
    void setUp() {
        superAdminToken = loginAs("super_admin", "admin123");
    }

    @AfterAll
    void tearDown() {
        // 清理本测试产生的业务日志
        jdbcTemplate.update("DELETE FROM sys_business_log WHERE operator_id = ?", SUPER_ADMIN_ID);
    }

    /**
     * 查询最新一条符合条件的业务日志。
     */
    private Map<String, Object> findLatestLog(String operationType, String operationObject) {
        return jdbcTemplate.queryForMap(
                "SELECT tenant_id, operator_id, operation_type, operation_object, object_id, " +
                        "after_value, result, error_msg, created_at " +
                        "FROM sys_business_log " +
                        "WHERE operator_id = ? AND operation_type = ? AND operation_object = ? " +
                        "ORDER BY created_at DESC LIMIT 1",
                SUPER_ADMIN_ID, operationType, operationObject);
    }

    @Test
    @DisplayName("异步业务日志被持久化且不阻塞接口")
    void asyncBusinessLogIsPersisted() {
        // Given
        Map<String, String> body = Map.of(
                "name", "张三",
                "phone", "13812345678",
                "email", "abc@example.com",
                "password", "MySecret123",
                "idCard", "110101199001011234");

        // When: 调用带 @BusinessLog 的接口并记录响应时间
        Response response = givenWithToken(superAdminToken)
                .body(body)
                .post("/api/v1/test-business-log");

        // Then: 接口立即返回成功
        response.then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.phone", equalTo("13812345678"));

        // Then: 响应时间小于 200ms（日志异步写入不阻塞主线程）
        assertTrue(response.time() < 200L,
                "接口响应应小于 200ms，实际为 " + response.time() + "ms");

        // Then: 等待异步日志写入后断言
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            Map<String, Object> log = findLatestLog("CREATE", "SensitiveCustomer");
            assertNotNull(log, "应存在业务日志记录");
            assertEquals(SUPER_ADMIN_ID, log.get("operator_id"));
            assertEquals(TENANT_ID, log.get("tenant_id"));
            assertEquals(1, log.get("result"));
        });
    }

    @Test
    @DisplayName("敏感字段在日志 after_value 中已脱敏")
    void sensitiveFieldsAreDesensitized() {
        // Given
        Map<String, String> body = Map.of(
                "name", "李四",
                "phone", "13812345678",
                "email", "abc@example.com",
                "password", "MySecret123",
                "idCard", "110101199001011234");

        // When
        givenWithToken(superAdminToken)
                .body(body)
                .post("/api/v1/test-business-log")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));

        // Then: 等待日志并读取 afterValue
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            Map<String, Object> log = findLatestLog("CREATE", "SensitiveCustomer");
            assertNotNull(log);
            String afterValue = (String) log.get("after_value");
            assertNotNull(afterValue);

            // 密码类字段统一替换为 ***
            assertTrue(afterValue.contains("\"password\":\"***\""), "密码应脱敏为 ***");
            // 手机号脱敏：138****5678
            assertTrue(afterValue.contains("138****5678"), "手机号应脱敏为 138****5678");
            // 邮箱脱敏：a***@example.com
            assertTrue(afterValue.contains("a***@example.com"), "邮箱应脱敏为 a***@example.com");
            // 身份证脱敏：110101********1234
            assertTrue(afterValue.contains("110101********1234"), "身份证应脱敏为 110101********1234");
            // 姓名为明文
            assertTrue(afterValue.contains("李四"), "姓名应为明文");
        });
    }

    @Test
    @DisplayName("失败操作记录错误信息")
    void failedOperationLogsError() {
        // Given
        Map<String, String> body = Map.of("key", "value");

        // When: 调用会抛异常的接口
        givenWithToken(superAdminToken)
                .body(body)
                .post("/api/v1/test-business-log/fail")
                .then()
                .statusCode(500);

        // Then: 等待失败日志
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            Map<String, Object> log = findLatestLog("CREATE", "FailingOperation");
            assertNotNull(log, "应存在失败业务日志");
            assertEquals(0, log.get("result"));
            String errorMsg = (String) log.get("error_msg");
            assertTrue(errorMsg != null && errorMsg.contains("业务操作失败"), "错误信息应包含异常消息");
        });
    }
}
