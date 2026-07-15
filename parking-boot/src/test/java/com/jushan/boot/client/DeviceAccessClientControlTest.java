package com.jushan.boot.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.common.BusinessException;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.DeviceAccessClientImpl;
import com.jushan.system.client.dto.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

/**
 * Device Access HTTP Client 设备控制测试（v0.4）。
 * <p>
 * 使用 WireMock 模拟 Device Access v0.4 设备控制接口，覆盖：
 * <ul>
 *   <li>开闸 200 成功</li>
 *   <li>关闸 200 成功</li>
 *   <li>显示屏文字/保存/配置 200 成功</li>
 *   <li>语音播报 200 成功</li>
 *   <li>DA 返回错误 500</li>
 *   <li>网络超时（UNCERTAIN）</li>
 *   <li>降级触发</li>
 *   <li>跨设备 SN 验证</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jushan.device-access.read-timeout=2000",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("Device Access HTTP Client 设备控制测试（v0.4）")
class DeviceAccessClientControlTest extends TestcontainersBaseTest {

    private static final String TEST_SN = "TEST-SN-001";
    private static final String METRIC_PREFIX = "device.access";

    static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wireMockServer.start();
    }

    @Autowired
    private DeviceAccessClient client;

    @Autowired
    private MeterRegistry meterRegistry;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("jushan.device-access.base-url", wireMockServer::baseUrl);
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        if (client instanceof DeviceAccessClientImpl impl) {
            impl.resetCircuitBreaker();
        }
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    // ==================== 开闸 ====================

    @Test
    @DisplayName("开闸 200 → 返回 CommandResultDTO")
    void shouldOpenGateWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(commandResult200Body("gate opened"))));

        CommandResultDTO result = client.openGate(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeviceCode()).isEqualTo(200);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(getCounterValue("calls.total", "status", "success")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("开闸 DA 返回非 200 code → 抛出 BusinessException")
    void shouldThrowWhenOpenGateDaError() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":503,\"message\":\"MQTT connection unavailable\"}")));

        assertThatThrownBy(() -> client.openGate(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MQTT connection unavailable");

        assertThat(getCounterValue("calls.errors", "error_type", "da_error_503")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("开闸设备命令超时 → 抛出 BusinessException（UNCERTAIN）")
    void shouldThrowTimeoutWhenOpenGateTimeout() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5000)
                        .withBody(commandResult200Body("gate opened"))));

        assertThatThrownBy(() -> client.openGate(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNCERTAIN");

        assertThat(getCounterValue("calls.errors", "error_type", "resource_access")).isGreaterThanOrEqualTo(1);
    }

    // ==================== 关闸 ====================

    @Test
    @DisplayName("关闸 200 → 返回 CommandResultDTO")
    void shouldCloseGateWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/close"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(commandResult200Body("gate closed"))));

        CommandResultDTO result = client.closeGate(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeviceCode()).isEqualTo(200);
        assertThat(result.isSuccessful()).isTrue();
    }

    // ==================== 显示屏文字 ====================

    @Test
    @DisplayName("显示屏文字 200 → 返回 DisplayResultDTO")
    void shouldDisplayTextWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/display/text"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(displayResult200Body())));

        DisplayTextRequest request = new DisplayTextRequest("欢迎光临", "HORIZONTAL", 16, "#FF0000");
        DisplayResultDTO result = client.displayText(TEST_SN, request);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.isSuccessful()).isTrue();
    }

    // ==================== 保存显示内容 ====================

    @Test
    @DisplayName("保存显示内容 200 → 返回 DisplayResultDTO")
    void shouldSaveDisplayWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/display/save"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(displayResult200Body())));

        DisplaySaveRequest request = new DisplaySaveRequest("临时车辆请扫码", 1);
        DisplayResultDTO result = client.saveDisplay(TEST_SN, request);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.isSuccessful()).isTrue();
    }

    // ==================== 显示屏配置 ====================

    @Test
    @DisplayName("显示屏配置 200 → 返回 DisplayResultDTO")
    void shouldDisplayConfigWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/display/config"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(displayResult200Body())));

        DisplayConfigRequest request = new DisplayConfigRequest("VOLUME", 80, null);
        DisplayResultDTO result = client.displayConfig(TEST_SN, request);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.isSuccessful()).isTrue();
    }

    // ==================== 语音播报 ====================

    @Test
    @DisplayName("语音播报 200 → 返回 VoiceResultDTO")
    void shouldVoiceControlWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/voice/control"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(voiceResult200Body())));

        VoiceControlRequest request = new VoiceControlRequest("PLAY", 1, "京A12345");
        VoiceResultDTO result = client.voiceControl(TEST_SN, request);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.isSuccessful()).isTrue();
    }

    // ==================== DA 错误场景 ====================

    @Test
    @DisplayName("DAClient 500 设备错误 → 抛出 BusinessException")
    void shouldThrowWhenDisplayText500() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/display/text"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":500,\"message\":\"device error\"}")));

        DisplayTextRequest request = new DisplayTextRequest("test", "HORIZONTAL", 16, "#FFF");

        assertThatThrownBy(() -> client.displayText(TEST_SN, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("device error");

        assertThat(getCounterValue("calls.errors", "error_type", "da_error_500")).isGreaterThanOrEqualTo(1);
    }

    // ==================== 降级策略 ====================

    @Test
    @DisplayName("连续 5 次开闸失败后触发降级 → 后续调用快速失败")
    void shouldTriggerCircuitBreakerAfter5OpenGateFailures() {
        for (int i = 0; i < 5; i++) {
            wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/open"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"code\":500,\"message\":\"device internal error\"}")));

            assertThatThrownBy(() -> client.openGate(TEST_SN))
                    .isInstanceOf(BusinessException.class);
        }

        if (client instanceof DeviceAccessClientImpl impl) {
            assertThat(impl.isCircuitOpen()).isTrue();
            assertThat(impl.getConsecutiveFailures()).isGreaterThanOrEqualTo(5);
        }

        // 降级后调用任何方法都会快速失败
        assertThatThrownBy(() -> client.openGate(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("降级");

        assertThat(getCounterValue("calls.errors", "error_type", "circuit_breaker")).isGreaterThanOrEqualTo(1);
    }

    // ==================== 跨设备调用验证 ====================

    @Test
    @DisplayName("不同 deviceSn 调用不同的 DA 端点")
    void shouldCallDifferentEndpointsForDifferentDeviceSns() {
        String sn1 = "SN-AAA-001";
        String sn2 = "SN-BBB-002";

        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + sn1 + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(commandResult200Body("sn1 opened"))));

        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + sn2 + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(commandResult200Body("sn2 opened"))));

        client.openGate(sn1);
        client.openGate(sn2);

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/devices/" + sn1 + "/gate/open")));
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/devices/" + sn2 + "/gate/open")));
    }

    // ==================== 无自动重试验证 ====================

    @Test
    @DisplayName("开闸请求失败后无自动重试 → 仅 1 次 HTTP 调用")
    void shouldNotRetryOpenGateOnFailure() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/open"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":503,\"message\":\"MQTT unavailable\"}")));

        assertThatThrownBy(() -> client.openGate(TEST_SN))
                .isInstanceOf(BusinessException.class);

        // 仅一次调用，无自动重试
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/gate/open")));
    }

    // ==================== 辅助方法 ====================

    private double getCounterValue(String metricName, String tagKey, String tagValue) {
        var counter = meterRegistry.find(METRIC_PREFIX + "." + metricName)
                .tag(tagKey, tagValue)
                .counter();
        return counter != null ? counter.count() : 0.0;
    }

    private String commandResult200Body(String message) {
        return "{\"code\":200,\"message\":\"success\",\"data\":{\"success\":true,\"deviceCode\":200,\"message\":\"" + message + "\"},\"timestamp\":\"2026-07-11 10:00:00\"}";
    }

    private String displayResult200Body() {
        return "{\"code\":200,\"message\":\"success\",\"data\":{\"success\":true,\"message\":\"ok\"},\"timestamp\":\"2026-07-11 10:00:00\"}";
    }

    private String voiceResult200Body() {
        return "{\"code\":200,\"message\":\"success\",\"data\":{\"success\":true,\"message\":\"playing\"},\"timestamp\":\"2026-07-11 10:00:00\"}";
    }
}
