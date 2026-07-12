package com.jushan.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智慧停车 SaaS 平台启动入口。
 * <p>
 * 组件扫描范围：{@code com.jushan}，覆盖：
 * <ul>
 *   <li>{@code com.jushan.framework} — 横切能力</li>
 *   <li>{@code com.jushan.common} — 通用类型</li>
 *   <li>各业务模块（后续按需引入）</li>
 * </ul>
 * <p>
 * <strong>P0 安全修复（FIX-07）：</strong>
 * {@code @MapperScan} 已移至 {@code MyBatisConfig}（条件化），
 * 轻量级测试可通过 {@code jushan.scan-mappers=false} 排除 MyBatis 依赖。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.jushan")
public class ParkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingApplication.class, args);
    }
}
