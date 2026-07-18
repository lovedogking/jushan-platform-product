package com.jushan.boot.client;

import com.jushan.system.client.MockDeviceAccessClient;
import com.jushan.system.client.dto.CommandResultDTO;
import com.jushan.system.client.dto.DeviceStatusDTO;
import com.jushan.system.client.dto.TimeSyncResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockDeviceAccessClient 单元测试（任务包 7-1）。
 * <p>
 * 验证 {@link MockDeviceAccessClient} 所有方法返回预期的模拟成功响应。
 * MockDeviceAccessClient 是纯 POJO 实现，无数据库/网络依赖，因此使用
 * 纯 JUnit 单元测试（无需 Spring 上下文或 Testcontainers）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@DisplayName("MockDeviceAccessClient 单元测试")
class MockDeviceAccessClientTest {

    private static final String TEST_SN = "MOCK-SN-001";

    private MockDeviceAccessClient client;

    @BeforeEach
    void setUp() {
        client = new MockDeviceAccessClient();
    }

    @Test
    @DisplayName("getStatus 返回 online=true，deviceSn 匹配")
    void shouldReturnOnlineStatus() {
        DeviceStatusDTO result = client.getStatus(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getDeviceSn()).isEqualTo(TEST_SN);
        assertThat(result.getOnline()).isTrue();
    }

    @Test
    @DisplayName("openGate(deviceSn) 返回 success=true，deviceCode=200，message 含 [MOCK]")
    void shouldReturnSuccessOnOpenGate() {
        CommandResultDTO result = client.openGate(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeviceCode()).isEqualTo(200);
        assertThat(result.getMessage()).startsWith("[MOCK]");
    }

    @Test
    @DisplayName("openGate(deviceSn, commandId) 返回 success=true，message 含 [MOCK] 和 commandId")
    void shouldReturnSuccessOnOpenGateWithCommandId() {
        String commandId = "mock-cmd-001";

        CommandResultDTO result = client.openGate(TEST_SN, commandId);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).contains("[MOCK]").contains(commandId);
    }

    @Test
    @DisplayName("closeGate(deviceSn) 返回 success=true")
    void shouldReturnSuccessOnCloseGate() {
        CommandResultDTO result = client.closeGate(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).startsWith("[MOCK]");
    }

    @Test
    @DisplayName("syncTime 返回 success=true")
    void shouldReturnSuccessOnSyncTime() {
        TimeSyncResultDTO result = client.syncTime(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).startsWith("[MOCK]");
    }
}
