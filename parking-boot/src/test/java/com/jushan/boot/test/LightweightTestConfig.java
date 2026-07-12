package com.jushan.boot.test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 轻量级测试配置（FIX-07）。
 * <p>
 * 仅扫描 parking-boot、parking-framework 和 parking-common，
 * 排除 parking-system（需要 DataSource/MyBatis）。
 * <p>
 * 用于不依赖数据库的测试：DemoControllerTest、TraceIdAndHttpStatusTest 等。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
    "com.jushan.boot",
    "com.jushan.framework",
    "com.jushan.common"
})
public class LightweightTestConfig {
}
