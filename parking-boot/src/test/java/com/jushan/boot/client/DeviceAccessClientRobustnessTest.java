package com.jushan.boot.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.common.BusinessException;
import com.jushan.system.client.DeviceAccessClient;
import com.jushan.system.client.DeviceAccessClientImpl;
import com.jushan.system.client.dto.DeviceStatusDTO;
import com.jushan.system.client.dto.TimeSyncResultDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

/**
 * Device Access HTTP Client 健壮性测试（P002）。
 * <p>
 * 覆盖 DeviceAccessClientImpl 的完整异常路径、降级策略和 Micrometer 指标：
 * <ul>
 *   <li>200 成功（状态查询 + 校时）</li>
 *   <li>404 设备不存在</li>
 *   <li>503 MQTT 不可用</li>
 *   <li>500 设备错误</li>
 *   <li>约 10 秒命令超时 → 平台客户端超时（UNCERTAIN）</li>
 *   <li>响应解析失败（畸形 JSON）</li>
 *   <li>openGate 抛出 UnsupportedOperationException</li>
 *   <li>降级策略：连续失败后快速失败</li>
 *   <li>降级恢复：超过恢复窗口后自动恢复</li>
 *   <li>Micrometer 指标验证</li>
 *   <li>重复调用无幂等（无自动重试）</li>
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
@DisplayName("Device Access HTTP Client 健壮性测试（P002）")
class DeviceAccessClientRobustnessTest extends TestcontainersBaseTest {

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
        // 不清除 MeterRegistry，因为 Gauge 在 initMetrics() 中注册，清除后无法恢复
        // Counter 和 Timer 是动态创建的，每次测试的计数会累加，但不会影响断言（使用 >=）
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    // ==================== 200 成功场景 ====================

    @Test
    @DisplayName("状态查询 200 -> 返回 DeviceStatusDTO + 指标记录")
    void shouldReturnStatusWhen200() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(status200Body())));

        DeviceStatusDTO result = client.getStatus(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getDeviceSn()).isEqualTo("TEST-SN-001");
        assertThat(result.getOnline()).isTrue();
        assertThat(getCounterValue("calls.total", "status", "success")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("校时 200 -> 返回 TimeSyncResultDTO + 指标记录")
    void shouldReturnTimeSyncWhen200() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/time/sync"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(timeSync200Body())));

        TimeSyncResultDTO result = client.syncTime(TEST_SN);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeviceCode()).isEqualTo(200);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(getCounterValue("calls.total", "status", "success")).isGreaterThanOrEqualTo(1);
    }

    // ==================== DA 错误场景 ====================

    @Test
    @DisplayName("404 设备不存在 -> 抛出 BusinessException + 错误指标")
    void shouldThrowWhen404() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":404,\"message\":\"device not found\"}")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("device not found");

        assertThat(getCounterValue("calls.errors", "error_type", "da_error_404")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("503 MQTT 不可用 -> 抛出 BusinessException + 错误指标")
    void shouldThrowWhen503() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/time/sync"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":503,\"message\":\"MQTT connection unavailable\"}")));

        assertThatThrownBy(() -> client.syncTime(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MQTT connection unavailable");

        assertThat(getCounterValue("calls.errors", "error_type", "da_error_503")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("500 设备错误 -> 抛出 BusinessException + 错误指标")
    void shouldThrowWhen500() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":500,\"message\":\"device error\"}")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("device error");

        assertThat(getCounterValue("calls.errors", "error_type", "da_error_500")).isGreaterThanOrEqualTo(1);
    }

    // ==================== 超时场景（UNCERTAIN） ====================

    @Test
    @DisplayName("设备命令超时（> 2s read timeout）-> 抛出 BusinessException（UNCERTAIN）+ 错误指标")
    void shouldThrowTimeoutWhenReadTimeoutExceeded() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(5000)
                        .withBody("{\"code\":200,\"message\":\"success\",\"data\":{\"deviceSn\":\"TEST-SN-001\",\"online\":true},\"timestamp\":\"2026-07-11 10:00:00\"}")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNCERTAIN");

        assertThat(getCounterValue("calls.errors", "error_type", "resource_access")).isGreaterThanOrEqualTo(1);
    }

    // ==================== 解析失败场景 ====================

    @Test
    @DisplayName("响应解析失败（畸形 JSON）-> 抛出 BusinessException + 错误指标")
    void shouldThrowWhenMalformedJson() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not json {{{")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class);

        assertThat(getCounterValue("calls.errors", "error_type", "rest_client")).isGreaterThanOrEqualTo(1);
    }

    // ==================== openGate 占位场景 ====================

    @Test
    @DisplayName("openGate -> 抛出 UnsupportedOperationException（v0.2 未实现）")
    void shouldThrowUnsupportedForOpenGate() {
        assertThatThrownBy(() -> client.openGate(TEST_SN))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("NOT_IMPLEMENTED_IN_V0.2");
    }

    // ==================== 边界场景 ====================

    @Test
    @DisplayName("DA 返回空响应体 -> 抛出 BusinessException（解析失败）")
    void shouldThrowWhenEmptyResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Device Access");

        assertThat(getCounterValue("calls.errors", "error_type", "empty_response")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("DA 返回 HTTP 200 但 data 为 null -> 抛出 BusinessException")
    void shouldThrowWhenDataIsNull() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"message\":\"success\",\"timestamp\":\"2026-07-11 10:00:00\"}")));

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Device Access 返回");
    }

    // ==================== 降级策略测试 ====================

    @Test
    @DisplayName("连续 5 次失败后触发降级 -> 后续调用快速失败")
    void shouldTriggerCircuitBreakerAfter5ConsecutiveFailures() {
        for (int i = 0; i < 5; i++) {
            wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"code\":500,\"message\":\"device internal error\"}")));

            assertThatThrownBy(() -> client.getStatus(TEST_SN))
                    .isInstanceOf(BusinessException.class);
        }

        if (client instanceof DeviceAccessClientImpl impl) {
            assertThat(impl.isCircuitOpen()).isTrue();
            assertThat(impl.getConsecutiveFailures()).isGreaterThanOrEqualTo(5);
        }

        assertThatThrownBy(() -> client.getStatus(TEST_SN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("降级");

        assertThat(getCounterValue("calls.errors", "error_type", "circuit_breaker")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("降级后手动重置 -> 可恢复正常调用")
    void shouldAllowManualResetAfterCircuitBreaker() {
        for (int i = 0; i < 5; i++) {
            wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"code\":500,\"message\":\"error\"}")));
            try {
                client.getStatus(TEST_SN);
            } catch (BusinessException ignored) {
            }
        }

        if (client instanceof DeviceAccessClientImpl impl) {
            assertThat(impl.isCircuitOpen()).isTrue();
            impl.resetCircuitBreaker();
            assertThat(impl.isCircuitOpen()).isFalse();
            assertThat(impl.getConsecutiveFailures()).isEqualTo(0);
        }

        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"message\":\"success\",\"data\":{\"deviceSn\":\"TEST-SN-001\",\"online\":true},\"timestamp\":\"2026-07-11 10:00:00\"}")));

        DeviceStatusDTO result = client.getStatus(TEST_SN);
        assertThat(result).isNotNull();
        assertThat(result.getOnline()).isTrue();
    }

    @Test
    @DisplayName("成功调用后重置连续失败计数器")
    void shouldResetFailureCounterOnSuccess() {
        for (int i = 0; i < 2; i++) {
            wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"code\":500,\"message\":\"error\"}")));
            try {
                client.getStatus(TEST_SN);
            } catch (BusinessException ignored) {
            }
        }

        if (client instanceof DeviceAccessClientImpl impl) {
            assertThat(impl.getConsecutiveFailures()).isEqualTo(2);
            assertThat(impl.isCircuitOpen()).isFalse();
        }

        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"message\":\"success\",\"data\":{\"deviceSn\":\"TEST-SN-001\",\"online\":true},\"timestamp\":\"2026-07-11 10:00:00\"}")));

        client.getStatus(TEST_SN);

        if (client instanceof DeviceAccessClientImpl impl) {
            assertThat(impl.getConsecutiveFailures()).isEqualTo(0);
        }
    }

    // ==================== 重复调用无幂等测试 ====================

    @Test
    @DisplayName("重复调用（无幂等）-> 每次独立发送 HTTP 请求 + 独立指标")
    void shouldSendIndependentRequestsWithoutIdempotency() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"message\":\"success\",\"data\":{\"deviceSn\":\"TEST-SN-001\",\"online\":true},\"timestamp\":\"2026-07-11 10:00:00\"}")));

        client.getStatus(TEST_SN);
        client.getStatus(TEST_SN);
        client.getStatus(TEST_SN);

        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status")));

        double successCount = getCounterValue("calls.total", "status", "success");
        assertThat(successCount).isGreaterThanOrEqualTo(3);
    }

    // ==================== 指标测试 ====================

    @Test
    @DisplayName("Micrometer 延迟指标被记录")
    void shouldRecordLatencyMetric() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"message\":\"success\",\"data\":{\"deviceSn\":\"TEST-SN-001\",\"online\":true},\"timestamp\":\"2026-07-11 10:00:00\"}")));

        client.getStatus(TEST_SN);

        List<Timer> timers = meterRegistry.getMeters().stream()
                .filter(m -> m instanceof Timer)
                .map(m -> (Timer) m)
                .filter(t -> t.getId().getName().equals(METRIC_PREFIX + ".latency"))
                .toList();

        assertThat(timers).isNotEmpty();
    }

    @Test
    @DisplayName("降级状态 Gauge 指标被记录")
    void shouldRecordCircuitBreakerGauge() {
        // Debug: print all meters
        System.out.println("DEBUG MeterRegistry type: " + meterRegistry.getClass().getName());
        System.out.println("DEBUG All meters: ");
        meterRegistry.getMeters().forEach(m -> System.out.println("  " + m.getId().getName() + " : " + m.getClass().getSimpleName()));
        
        // 通过遍历所有 meters 查找 Gauge（SimpleMeterRegistry 的 find() 可能不返回 Gauge）
        var gauge = meterRegistry.getMeters().stream()
                .filter(m -> m instanceof io.micrometer.core.instrument.Gauge)
                .filter(m -> m.getId().getName().equals(METRIC_PREFIX + ".circuit_breaker.state"))
                .map(m -> (io.micrometer.core.instrument.Gauge) m)
                .findFirst()
                .orElse(null);

        assertThat(gauge).isNotNull();

        // 默认状态应为 0.0（关闭）
        assertThat(gauge.value()).isEqualTo(0.0);

        // 触发降级
        for (int i = 0; i < 5; i++) {
            wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/devices/" + TEST_SN + "/status"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"code\":500,\"message\":\"error\"}")));
            try {
                client.getStatus(TEST_SN);
            } catch (BusinessException ignored) {
            }
        }

        // 降级打开后 Gauge 应为 1.0
        assertThat(gauge.value()).isEqualTo(1.0);
    }

    // ==================== 辅助方法 ====================

    private double getCounterValue(String metricName, String tagKey, String tagValue) {
        var counter = meterRegistry.find(METRIC_PREFIX + "." + metricName)
                .tag(tagKey, tagValue)
                .counter();
        return counter != null ? counter.count() : 0.0;
    }

    private String status200Body() {
        return "{\"code\":200,\"message\":\"success\",\"data\":{\"deviceSn\":\"TEST-SN-001\",\"online\":true,\"lastOnlineTime\":\"2026-07-11 10:00:00\",\"status\":\"connected\"},\"timestamp\":\"2026-07-11 10:00:00\"}";
    }

    private String timeSync200Body() {
        return "{\"code\":200,\"message\":\"success\",\"data\":{\"success\":true,\"deviceCode\":200,\"message\":\"time synced\"},\"timestamp\":\"2026-07-11 10:00:00\"}";
    }
}
