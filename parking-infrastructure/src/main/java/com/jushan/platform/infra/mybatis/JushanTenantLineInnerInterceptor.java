package com.jushan.platform.infra.mybatis;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.jushan.common.mybatis.TenantIgnoreHolder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 自定义多租户行级拦截器，支持通过 {@link TenantIgnoreHolder} 跳过租户条件注入。
 * <p>
 * 当 {@link TenantIgnoreHolder#isIgnored()} 为 true 时（由 AOP 切面设置），
 * 不追加租户条件，用于跨租户查询场景（如 Mock 接口、设备校验等）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class JushanTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    public JushanTenantLineInnerInterceptor(com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler tenantLineHandler) {
        super(tenantLineHandler);
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (TenantIgnoreHolder.isIgnored()) {
            return;
        }
        super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        if (TenantIgnoreHolder.isIgnored()) {
            return;
        }
        super.beforePrepare(sh, connection, transactionTimeout);
    }
}
