package com.jushan.boot.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.common.BusinessException;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.dto.DeviceStatusDTO;
import com.jushan.system.client.dto.TimeSyncResultDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

/**
 * Device Access HTTP Client 集成测试（T23）。
 * <p>
 * 使用 WireMock 模拟 Device Access v0.2 服务，覆盖：
 * <ul>
 *   <li>200 成功（状态查询 + 校时）</li>
 *   <li>404 设备不存在</li>
 *   <li>503 MQTT 不可用</li>
 *   <li>500 设备错误</li>
 *   <li>约 10 秒命令超时 → 平台客户端超时（UNCERTAIN）</li>
 *   <li>响应解析失败（畸形 JSON）</li>
 *   <li>openGate 抛出 UnsupportedOperationException</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // 测试使用短超时以快速验证超时场景
        "jushan.device-access.read-timeout=2000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("Device Access HTTP Client 集成测试")
class DeviceAccessClientTest extends TestcontainersBaseTest {

    private static final String TEST_SN = "TEST-SN-001";

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /** WireMock 服务器在静态初始化块中启动，确保早于 Spring 上下文初始化 */
    static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMockServer.start();
    }

    @Autowired
    private DeviceAccessClient client;

    /**
     * 将 WireMock 动态端口注入为 Device Access base-url。
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("jushan.device-access.base-url", wireMockServer::baseUrl);
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    // ==================== 200 成功场景 ====================

    @Test
    @DisplayName("状态查询 200 → 返回 DeviceStatusDTO")
    void shouldReturnStatusWhen200() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "TEST-SN-001",
                                        "online": true,
                                        "lastOnlineTime": "2026-07-11 10:00:00",
                                        "status": "connected"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        DeviceStatusDTO result = client.getStatus(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getDeviceSn()).isEqualTo("TEST-SN-001");
        assertThat(result.getOnline()).isTrue();
    }

    @Test
    @DisplayName("校时 200 → 返回 TimeSyncResultDTO")
    void shouldReturnTimeSyncWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/time/sync"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "success": true,
                                        "deviceCode": 200,
                                        "message": "time synced"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        TimeSyncResultDTO result = client.syncTime(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeviceCode()).isEqualTo(200);
        assertThat(result.isSuccessful()).isTrue();
    }

    // ==================== DA 错误场景 ====================

    @Test
    @DisplayName("404 设备不存在 → 抛出 BusinessException")
    void shouldThrowWhen404() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 404,
                                    "message": "device not found"
                                }""")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("device not found");
    }

    @Test
    @DisplayName("503 MQTT 不可用 → 抛出 BusinessException")
    void shouldThrowWhen503() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/time/sync"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 503,
                                    "message": "MQTT connection unavailable"
                                }""")));

        assertThatThrownBy(() -> client.syncTime(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MQTT connection unavailable");
    }

    @Test
    @DisplayName("500 设备错误 → 抛出 BusinessException")
    void shouldThrowWhen500() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 500,
                                    "message": "device error"
                                }""")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("device error");
    }

    // ==================== 超时场景 ====================

    @Test
    @DisplayName("设备命令超时（> 2s read timeout）→ 抛出 BusinessException（UNCERTAIN）")
    void shouldThrowTimeoutWhenReadTimeoutExceeded() {
        // WireMock 延迟 5 秒，超过 read-timeout 2 秒
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5000)
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "deviceSn": "TEST-SN-001",
                                        "online": true,
                                        "lastOnlineTime": "2026-07-11 10:00:00"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNCERTAIN");
    }

    // ==================== 解析失败场景 ====================

    @Test
    @DisplayName("响应解析失败（畸形 JSON）→ 抛出 BusinessException")
    void shouldThrowWhenMalformedJson() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not json {{{")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class);
    }

    // ==================== openGate v0.4 场景 ====================

    @Test
    @DisplayName("开闸 200 → 返回 CommandResultDTO")
    void shouldReturnCommandResultWhenOpenGate200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "data": {
                                        "success": true,
                                        "deviceCode": 200,
                                        "message": "gate opened"
                                    },
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        com.jushan.system.client.dto.CommandResultDTO result = client.openGate(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeviceCode()).isEqualTo(200);
        assertThat(result.isSuccessful()).isTrue();
    }

    // ==================== 边界场景 ====================

    @Test
    @DisplayName("DA 返回空响应体 → 抛出 BusinessException（解析失败）")
    void shouldThrowWhenEmptyResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Device Access");
    }

    @Test
    @DisplayName("DA 返回 HTTP 200 但 data 为 null → 抛出 BusinessException")
    void shouldThrowWhenDataIsNull() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "code": 200,
                                    "message": "success",
                                    "timestamp": "2026-07-11 10:00:00"
                                }""")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Device Access 返回");
    }
}
