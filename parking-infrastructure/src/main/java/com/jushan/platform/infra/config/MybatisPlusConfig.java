package com.jushan.platform.infra.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.jushan.platform.infra.mybatis.JushanTenantLineHandler;
import com.jushan.platform.infra.mybatis.JushanTenantLineInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 注册多租户拦截器、分页拦截器和全局配置（Snowflake ID、逻辑删除）。
 * <ul>
 *   <li>多租户拦截器：自动为 SQL 追加 tenant_id 条件</li>
 *   <li>分页拦截器：支持 MyBatis-Plus 分页查询</li>
 *   <li>全局配置：Snowflake 分布式ID生成策略、逻辑删除字段配置</li>
 * </ul>
 * <p>
 * 超级管理员跨租户查询时，在 Mapper 方法上添加 {@link InterceptorIgnore}(tenantLine = "1") 注解跳过租户拦截。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 全局配置。
     * <p>
     * - 主键策略：Snowflake 分布式ID（ASSIGN_ID）
     * - 逻辑删除字段：deleted_at（非空表示已删除）
     */
    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        // 主键策略：Snowflake 分布式ID
        dbConfig.setIdType(com.baomidou.mybatisplus.annotation.IdType.ASSIGN_ID);
        // 逻辑删除字段：deleted_at（值为非空表示已删除）
        dbConfig.setLogicDeleteField("deletedAt");
        dbConfig.setLogicDeleteValue("now()");
        dbConfig.setLogicNotDeleteValue("NULL");
        globalConfig.setDbConfig(dbConfig);
        return globalConfig;
    }

    /**
     * 配置 MyBatis-Plus 拦截器链。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户拦截器（支持 TenantIgnoreHolder 跳过）
        JushanTenantLineInnerInterceptor tenantInterceptor = new JushanTenantLineInnerInterceptor(
                new JushanTenantLineHandler()
        );
        interceptor.addInnerInterceptor(tenantInterceptor);

        // 分页拦截器
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInterceptor.setMaxLimit(1000L); // 最大单页限制
        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}
