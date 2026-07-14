package com.jushan.platform.infra.mybatis;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.jushan.common.auth.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 多租户行级隔离处理器。
 * <p>
 * 为 {@link TenantLineInnerInterceptor} 提供租户 ID 来源与表忽略策略。
 * <ul>
 *   <li>租户 ID 从 {@link TenantContext#getTenantId()} 获取，禁止从前端参数读取。</li>
 *   <li>平台用户（tenantId == null）默认不能访问业务表，fail-close。</li>
 *   <li>全局表（无 tenant_id 列）通过白名单跳过。</li>
 *   <li>超级管理员跨租户查询使用 {@link InterceptorIgnore} 注解跳过拦截。</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class JushanTenantLineHandler implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

    private static final Logger log = LoggerFactory.getLogger(JushanTenantLineHandler.class);

    private static final String DEFAULT_TENANT_ID_COLUMN = "tenant_id";

    /**
     * 全局表白名单：无 tenant_id 列或允许跨租户访问的表。
     */
    private static final Set<String> IGNORE_TABLES = Stream.of(
            // Sprint 1 新增全局表
            "sys_tenant",
            "sys_auth_code",
            "sys_business_log",
            // 遗留全局表
            "tenant",
            "sys_role",
            "sys_permission",
            "sys_role_permission",
            "sys_config",
            "sys_login_log",
            "device_vendor",
            "device_model"
    ).collect(Collectors.toSet());

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.getTenantId();
        log.info("JushanTenantLineHandler 获取租户ID: tenantId={}", tenantId);
        if (tenantId == null) {
            // 平台用户 tenantId 为 null，SQL 会追加 tenant_id = NULL。
            // 这是 fail-closed 的：业务表通常没有 tenant_id = NULL 的记录。
            return null;
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return DEFAULT_TENANT_ID_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return false;
        }
        String normalized = tableName.trim().toLowerCase();
        return IGNORE_TABLES.contains(normalized);
    }
}
