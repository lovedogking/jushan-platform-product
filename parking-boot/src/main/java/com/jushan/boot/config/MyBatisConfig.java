package com.jushan.boot.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描与分页配置。
 * <p>
 * <strong>P0 安全修复（FIX-07）：</strong> 当通过
 * {@code spring.autoconfigure.exclude} 排除 DataSource 时，
 * 需要禁用 @MapperScan 以避免创建需要 SqlSessionFactory 的 Mapper 代理。
 * <p>
 * <strong>FIX-02 分页修复</strong>：注册 {@link PaginationInnerInterceptor}，
 * 使 MyBatis-Plus 分页查询的 {@code total} 字段正确返回总记录数。
 * 此前未注册分页插件导致所有分页查询的 {@code total} 恒为 0。
 * <p>
 * 轻量级测试（如 DemoControllerTest）可通过设置
 * {@code jushan.scan-mappers=false} 来阻止 Mapper 扫描。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "jushan.scan-mappers", havingValue = "true", matchIfMissing = true)
@MapperScan("com.jushan.**.mapper")
public class MyBatisConfig {

    /**
     * MyBatis-Plus 分页插件。
     * <p>
     * 注册后，{@code selectPage} 自动执行 COUNT 查询并填充 {@code IPage.total}。
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 溢出控制：最大每页 200 条，防止深度分页拖垮数据库
        paginationInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
