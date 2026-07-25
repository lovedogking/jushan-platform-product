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
 *   <li>平台用户和岗亭管理员（tenantId == null）跳过租户拦截，由 Service 层控制数据范围。</li>
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
            "device_model",
            // Service 层已自行处理租户隔离的业务表
            "company",
            "sys_admin_account",
            "plate_binding",
            "vehicle",
            // 系统任务写入的快照表，tenant_id 由轮询任务填充，租户查询不应过滤
            "device_status_snapshot"
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
        // 平台用户（super_admin）和岗亭管理员均为跨租户设计，跳过所有租户拦截
        // 数据范围由 Service 层（ParkingLotScopeResolver / DataScope）控制
        if (TenantContext.isPlatformUser() || TenantContext.isBoothUser()) {
            return true;
        }
        if (tableName == null) {
            return false;
        }
        String normalized = tableName.trim().toLowerCase();
        return IGNORE_TABLES.contains(normalized);
    }
}
