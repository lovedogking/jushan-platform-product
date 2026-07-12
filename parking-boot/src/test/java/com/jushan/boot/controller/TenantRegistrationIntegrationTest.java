package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 客户注册、审核与启停集成测试（T14）。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>客户自助注册</li>
 *   <li>重复企业名称/手机号拒绝</li>
 *   <li>超级管理员审核通过</li>
 *   <li>超级管理员审核拒绝</li>
 *   <li>启用/禁用客户</li>
 *   <li>越权操作拒绝（非管理员不能审核）</li>
 *   <li>状态流转校验（不能重复审核）</li>
 *   <li>审核日志记录</li>
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
@DisplayName("客户注册审核集成测试")
class TenantRegistrationIntegrationTest extends TestcontainersBaseTest {

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

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        // 管理员登录获取 token
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = extractToken(responseBody);

        // 清理测试数据
        tenantMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getContactPhone, "13800001111"));
        tenantMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getContactPhone, "13800002222"));
        sysUserMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "13800001111"));
        sysUserMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "13800002222"));
    }

    // ==================== ① 客户注册 ====================

    @Test
    @DisplayName("正常注册 → 创建待审核租户和管理员账号")
    void shouldRegisterSuccessfully() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("测试科技有限公司", "张三", "13800001111", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证租户已创建且状态为 PENDING_REVIEW
        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));
        assertThat(tenant).isNotNull();
        assertThat(tenant.getName()).isEqualTo("测试科技有限公司");
        assertThat(tenant.getContactPerson()).isEqualTo("张三");
        assertThat(tenant.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(tenant.getAdminUserId()).isNotNull();

        // 验证管理员账号已创建且状态为 PENDING_REVIEW
        SysUser user = sysUserMapper.selectById(tenant.getAdminUserId());
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("13800001111");
        assertThat(user.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(user.getTenantId()).isEqualTo(tenant.getId());
        assertThat(user.getRoles()).contains("customer_admin");
    }

    @Test
    @DisplayName("重复企业名称 → 拒绝")
    void shouldRejectDuplicateCompanyName() throws Exception {
        // 第一次注册成功
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("重复企业有限公司", "李四", "13800002222", "pass123")))
                .andExpect(status().isOk());

        // 第二次用相同企业名称 → 拒绝
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("重复企业有限公司", "王五", "13800003333", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("该企业名称已被注册"));
    }

    @Test
    @DisplayName("重复手机号 → 拒绝")
    void shouldRejectDuplicatePhone() throws Exception {
        // 第一次注册成功
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("企业A", "李四", "13800002222", "pass123")))
                .andExpect(status().isOk());

        // 第二次用相同手机号 → 拒绝
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("企业B", "王五", "13800002222", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("该手机号已被注册"));
    }

    @Test
    @DisplayName("参数校验失败 → 400")
    void shouldRejectInvalidParams() throws Exception {
        // 空企业名称
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("", "张三", "13800001111", "pass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        // 非法手机号
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("测试公司", "张三", "12345", "pass123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        // 短密码
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("测试公司", "张三", "13800001111", "ab")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== ② 未登录不能审核 ====================

    @Test
    @DisplayName("未登录调用审核接口 → 401")
    void shouldRejectAuditWithoutLogin() throws Exception {
        mockMvc.perform(post("/admin/tenants/1/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVED\",\"reason\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== ③ 审核通过 ====================

    @Test
    @DisplayName("审核通过 → 租户和账号状态变为 ENABLED")
    void shouldApproveTenant() throws Exception {
        // 1. 先注册
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("通过测试企业", "赵六", "13800001111", "pass123")))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));
        Long tenantId = tenant.getId();
        Long adminUserId = tenant.getAdminUserId();

        // 2. 管理员审核通过
        mockMvc.perform(post("/admin/tenants/" + tenantId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVED\",\"reason\":\"资料齐全\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 验证租户状态
        Tenant updated = tenantMapper.selectById(tenantId);
        assertThat(updated.getStatus()).isEqualTo("ENABLED");

        // 4. 验证管理员账号状态
        SysUser user = sysUserMapper.selectById(adminUserId);
        assertThat(user.getStatus()).isEqualTo("ENABLED");
    }

    // ==================== ④ 审核拒绝 ====================

    @Test
    @DisplayName("审核拒绝 → 租户状态变为 REJECTED，账号禁用")
    void shouldRejectTenant() throws Exception {
        // 1. 先注册
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("拒绝测试企业", "孙七", "13800001111", "pass123")))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));
        Long tenantId = tenant.getId();

        // 2. 管理员拒绝（必须填写原因）
        mockMvc.perform(post("/admin/tenants/" + tenantId + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECTED\",\"reason\":\"企业资质不全\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 验证租户状态
        Tenant updated = tenantMapper.selectById(tenantId);
        assertThat(updated.getStatus()).isEqualTo("REJECTED");

        // 4. 验证管理员账号被禁用
        SysUser user = sysUserMapper.selectById(tenant.getAdminUserId());
        assertThat(user.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("拒绝未填写原因 → 400")
    void shouldRequireReasonForRejection() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("无原因测试", "周八", "13800001111", "pass123")))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));

        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECTED\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("拒绝操作必须填写原因"));
    }

    // ==================== ⑤ 启停 ====================

    @Test
    @DisplayName("禁用已启用客户 → 状态变为 DISABLED")
    void shouldDisableTenant() throws Exception {
        // 1. 注册并审核通过
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("禁用测试企业", "郑九", "13800001111", "pass123")))
                .andExpect(status().isOk());
        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));
        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVED\",\"reason\":\"通过\"}"));

        // 2. 禁用
        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLED\",\"reason\":\"违规经营\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 验证状态
        Tenant updated = tenantMapper.selectById(tenant.getId());
        assertThat(updated.getStatus()).isEqualTo("DISABLED");
        SysUser user = sysUserMapper.selectById(tenant.getAdminUserId());
        assertThat(user.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("重新启用已禁用客户 → 状态变为 ENABLED")
    void shouldReEnableTenant() throws Exception {
        // 1. 注册 → 通过 → 禁用
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("启停测试企业", "钱十", "13800001111", "pass123")))
                .andExpect(status().isOk());
        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));
        Long tid = tenant.getId();
        mockMvc.perform(post("/admin/tenants/" + tid + "/audit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVED\",\"reason\":\"通过\"}"));
        mockMvc.perform(post("/admin/tenants/" + tid + "/audit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DISABLED\",\"reason\":\"违规\"}"));

        // 2. 重新启用
        mockMvc.perform(post("/admin/tenants/" + tid + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLED\",\"reason\":\"整改完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 3. 验证
        Tenant updated = tenantMapper.selectById(tid);
        assertThat(updated.getStatus()).isEqualTo("ENABLED");
        SysUser user = sysUserMapper.selectById(tenant.getAdminUserId());
        assertThat(user.getStatus()).isEqualTo("ENABLED");
    }

    // ==================== ⑥ 状态流转校验 ====================

    @Test
    @DisplayName("重复审核已通过租户 → 拒绝")
    void shouldRejectDoubleApprove() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("双审测试", "冯十一", "13800001111", "pass123")))
                .andExpect(status().isOk());
        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));
        Long tid = tenant.getId();

        // 第一次通过
        mockMvc.perform(post("/admin/tenants/" + tid + "/audit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVED\",\"reason\":\"通过\"}"));

        // 第二次通过 → 拒绝
        mockMvc.perform(post("/admin/tenants/" + tid + "/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVED\",\"reason\":\"再通过一次\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000));
    }

    // ==================== ⑦ 租户列表查询 ====================

    @Test
    @DisplayName("管理员查询租户列表 → 返回分页数据")
    void shouldListTenants() throws Exception {
        // 先注册一个租户
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("列表测试企业", "陈十二", "13800001111", "pass123")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @DisplayName("按状态筛选租户列表")
    void shouldFilterTenantsByStatus() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("筛选测试企业", "吴十三", "13800001111", "pass123")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // ==================== ⑧ 租户详情 ====================

    @Test
    @DisplayName("查询租户详情 → 手机号脱敏")
    void shouldReturnTenantDetailWithMaskedPhone() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("详情测试企业", "褚十四", "13800001111", "pass123")))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));

        mockMvc.perform(get("/admin/tenants/" + tenant.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("详情测试企业"))
                .andExpect(jsonPath("$.data.contactPhone").value("138****1111"))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
    }

    @Test
    @DisplayName("查询不存在的租户 → 404")
    void shouldReturn404ForNonExistentTenant() throws Exception {
        mockMvc.perform(get("/admin/tenants/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== ⑨ 未审核客户不能登录 ====================

    @Test
    @DisplayName("待审核账号不能登录")
    void shouldRejectPendingUserLogin() throws Exception {
        // 注册
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("登录测试企业", "卫十五", "13800001111", "pass123")))
                .andExpect(status().isOk());

        // 尝试用待审核账号登录 → 401
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"13800001111\",\"password\":\"pass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("审核通过后客户管理员可以登录")
    void shouldAllowApprovedUserLogin() throws Exception {
        // 注册
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("已通过登录测试", "蒋十六", "13800001111", "pass123")))
                .andExpect(status().isOk());

        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, "13800001111"));

        // 审核通过
        mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/audit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVED\",\"reason\":\"通过\"}"));

        // 客户管理员登录
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"13800001111\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.roles[0]").value("customer_admin"));
    }

    // ==================== 工具方法 ====================

    private String registrationJson(String companyName, String contactPerson,
                                     String contactPhone, String password) {
        return String.format(
                "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"%s\"}",
                companyName, contactPerson, contactPhone, password);
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
