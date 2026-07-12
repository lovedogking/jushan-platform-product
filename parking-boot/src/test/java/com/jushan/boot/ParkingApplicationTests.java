package com.jushan.boot;

import com.jushan.boot.test.TestcontainersBaseTest;
import org.junit.jupiter.api.Test;

/**
 * 最小上下文加载测试。
 * <p>
 * 验证 Spring 容器可正常启动，模块依赖方向正确。
 * <p>
 * <strong>FIX-07：</strong>使用完整 Spring Boot 上下文（Testcontainers MySQL）。
 * 与项目其他集成测试一致，确保 Bean 装配和模块依赖验证可靠。
 */
class ParkingApplicationTests extends TestcontainersBaseTest {

    @Test
    void contextLoads() {
        // 上下文成功加载即为通过
    }
}
