package com.jushan.boot.tenant;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.dto.RegisterRequest;
import com.jushan.system.dto.TenantAuditRequest;
import com.jushan.system.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 租户审核通过服务层集成测试。
 * <p>
 * 复现 BUG：租户注册后，超级管理员点击"通过"提示"系统繁忙，请稍后重试"。
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("TenantService.audit(APPROVED) - BUG 复现")
class TenantServiceAuditApprovedTest extends TestcontainersBaseTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 模拟平台超级管理员登录上下文
        TenantContext.set(new TenantContext.Snapshot(
                null,           // tenantId：平台用户为 null
                1L,             // userId：super_admin
                "platform",     // userType
                "[\"super_admin\"]",
                "tenant:write"
        ));
    }

    @Test
    @DisplayName("审核通过应成功创建 sys_admin_account 及角色绑定")
    void shouldApproveTenantSuccessfully() {
        String phone = "139" + (System.nanoTime() % 100000000);

        // 1. 注册租户
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setCompanyName("审核通过测试企业");
        registerRequest.setContactPerson("测试联系人");
        registerRequest.setContactPhone(phone);
        registerRequest.setPassword("Test1234!");
        tenantService.register(registerRequest);

        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM tenant WHERE contact_phone = ?", Long.class, phone);
        assertThat(tenantId).isNotNull();

        // 2. 审核通过
        TenantAuditRequest auditRequest = new TenantAuditRequest();
        auditRequest.setAction("APPROVED");
        auditRequest.setReason("资料齐全");

        assertThatNoException().isThrownBy(() -> tenantService.audit(tenantId, auditRequest));

        // 3. 验证状态
        String tenantStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM tenant WHERE id = ?", String.class, tenantId);
        assertThat(tenantStatus).isEqualTo("ENABLED");

        // 4. 验证管理员账号已同步
        Long accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_admin_account WHERE username = ?", Long.class, phone);
        assertThat(accountCount).isEqualTo(1L);

        // 5. 验证角色绑定已创建
        Long bindingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_admin_account_role r " +
                        "JOIN sys_admin_account a ON r.admin_account_id = a.id " +
                        "WHERE a.username = ?", Long.class, phone);
        assertThat(bindingCount).isEqualTo(1L);
    }
}
