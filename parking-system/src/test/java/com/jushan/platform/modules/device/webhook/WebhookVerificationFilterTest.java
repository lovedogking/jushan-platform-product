package com.jushan.platform.modules.device.webhook;

import com.jushan.framework.web.CachedBodyHttpServletRequest;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.WebhookSecret;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.WebhookSecretMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebhookVerificationFilter 单元测试。
 * <p>
 * 覆盖四种拒绝场景和合法签名场景，以及 nonce 防重放。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookVerificationFilterTest {

    private static final String SECRET = "test-webhook-secret-32bytes!!!";
    private static final String DEVICE_SN = "SN-TEST-001";
    private static final Long PARKING_LOT_ID = 100L;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private WebhookSecretMapper webhookSecretMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private FilterChain filterChain;

    private WebhookVerificationFilter filter;

    @BeforeEach
    void setUp() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        when(deviceMapper.selectByDeviceSn(DEVICE_SN)).thenReturn(createDevice());

        WebhookSecret secretEntity = new WebhookSecret();
        secretEntity.setSecret(SECRET);
        secretEntity.setParkingLotId(PARKING_LOT_ID);
        secretEntity.setStatus(1);
        when(webhookSecretMapper.selectByParkingLotId(PARKING_LOT_ID)).thenReturn(secretEntity);

        filter = new WebhookVerificationFilter(
                deviceMapper, webhookSecretMapper, redisTemplateProvider, true);
    }

    private Device createDevice() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceSn(DEVICE_SN);
        device.setParkingLotId(PARKING_LOT_ID);
        device.setTenantId(1L);
        return device;
    }

    private String createRequestBody(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"PLATE_RECOGNIZED\",\"deviceSn\":\"" + DEVICE_SN + "\"}";
    }

    private MockHttpServletRequest createRequest(String body, long timestamp, String nonce, String sign) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/device-webhook/events");
        request.setServletPath("/api/v1/device-webhook/events");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Nonce", nonce);
        if (sign != null) {
            request.addHeader("X-Sign", sign);
        }
        return request;
    }

    // ==================== 合法签名 → 通过 ====================

    @Test
    @DisplayName("合法签名：请求正常通过过滤器")
    void shouldPassValidSignature() throws Exception {
        String body = createRequestBody("evt-001");
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-valid-001";

        String sign = WebhookSignatureTool.computeSignature(SECRET, timestamp, nonce, body);
        MockHttpServletRequest request = createRequest(body, timestamp, nonce, sign);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(any(), eq(response));
    }

    // ==================== 无签名 → 401 ====================

    @Test
    @DisplayName("无 X-Sign 头：返回 401")
    void shouldRejectMissingSignHeader() throws Exception {
        String body = createRequestBody("evt-002");
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-no-sign";

        MockHttpServletRequest request = createRequest(body, timestamp, nonce, null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("401");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("空字符串 X-Sign：返回 401")
    void shouldRejectBlankSignHeader() throws Exception {
        String body = createRequestBody("evt-002b");
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-blank-sign";

        MockHttpServletRequest request = createRequest(body, timestamp, nonce, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== 错误签名 → 401 ====================

    @Test
    @DisplayName("错误签名（篡改请求体）：返回 401")
    void shouldRejectWrongSignature() throws Exception {
        String body = createRequestBody("evt-003");
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-wrong-sign";

        String wrongSign = WebhookSignatureTool.computeSignature("wrong-secret", timestamp, nonce, body);
        MockHttpServletRequest request = createRequest(body, timestamp, nonce, wrongSign);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("错误签名（篡改时间戳）：返回 401")
    void shouldRejectTamperedTimestamp() throws Exception {
        String body = createRequestBody("evt-004");
        long originalTimestamp = System.currentTimeMillis();
        long tamperedTimestamp = System.currentTimeMillis() - 100000;
        String nonce = "nonce-tampered-ts";

        String signWithOriginalTs = WebhookSignatureTool.computeSignature(SECRET, originalTimestamp, nonce, body);
        MockHttpServletRequest request = createRequest(body, tamperedTimestamp, nonce, signWithOriginalTs);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== 过期时间戳 → 401 ====================

    @Test
    @DisplayName("过期时间戳（>5分钟）：返回 401")
    void shouldRejectExpiredTimestamp() throws Exception {
        String body = createRequestBody("evt-005");
        long expiredTimestamp = System.currentTimeMillis() - Duration.ofMinutes(6).toMillis();
        String nonce = "nonce-expired-ts";

        String sign = WebhookSignatureTool.computeSignature(SECRET, expiredTimestamp, nonce, body);
        MockHttpServletRequest request = createRequest(body, expiredTimestamp, nonce, sign);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("未来时间戳（>5分钟）：返回 401")
    void shouldRejectFutureTimestamp() throws Exception {
        String body = createRequestBody("evt-006");
        long futureTimestamp = System.currentTimeMillis() + Duration.ofMinutes(6).toMillis();
        String nonce = "nonce-future-ts";

        String sign = WebhookSignatureTool.computeSignature(SECRET, futureTimestamp, nonce, body);
        MockHttpServletRequest request = createRequest(body, futureTimestamp, nonce, sign);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== Nonce 重放 → 401 ====================

    @Test
    @DisplayName("重复 nonce：返回 401")
    void shouldRejectReplayedNonce() throws Exception {
        String body = createRequestBody("evt-007");
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-replayed";
        String sign = WebhookSignatureTool.computeSignature(SECRET, timestamp, nonce, body);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        MockHttpServletRequest request = createRequest(body, timestamp, nonce, sign);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== 缺少时间戳 → 401 ====================

    @Test
    @DisplayName("缺少 X-Timestamp：返回 401")
    void shouldRejectMissingTimestamp() throws Exception {
        String body = createRequestBody("evt-008");
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-no-ts";
        String sign = WebhookSignatureTool.computeSignature(SECRET, timestamp, nonce, body);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/device-webhook/events");
        request.setServletPath("/api/v1/device-webhook/events");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Sign", sign);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("缺少 X-Nonce：返回 401")
    void shouldRejectMissingNonce() throws Exception {
        String body = createRequestBody("evt-009");
        long timestamp = System.currentTimeMillis();
        String nonce = "nonce-missing";
        String sign = WebhookSignatureTool.computeSignature(SECRET, timestamp, nonce, body);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/device-webhook/events");
        request.setServletPath("/api/v1/device-webhook/events");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Sign", sign);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== 非 webhook 路径 → 跳过 ====================

    @Test
    @DisplayName("非 webhook 路径：shouldNotFilter 返回 true")
    void shouldSkipNonWebhookPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/health");
        request.setServletPath("/api/v1/health");

        boolean shouldNotFilter = filter.shouldNotFilter(request);

        assertThat(shouldNotFilter).isTrue();
    }

    // ==================== 签名禁用 → 跳过 ====================

    @Test
    @DisplayName("签名校验禁用：放行所有请求")
    void shouldPassWhenSignatureDisabled() throws Exception {
        WebhookVerificationFilter disabledFilter = new WebhookVerificationFilter(
                deviceMapper, webhookSecretMapper, redisTemplateProvider, false);

        String body = createRequestBody("evt-010");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/device-webhook/events");
        request.setServletPath("/api/v1/device-webhook/events");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        disabledFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    // ==================== HMAC 计算工具测试 ====================

    @Test
    @DisplayName("computeHmacSha256：相同输入产生相同签名")
    void shouldProduceConsistentSignature() {
        String payload1 = "1234567890" + "nonce-abc" + "{\"test\":true}";
        String payload2 = "1234567890" + "nonce-abc" + "{\"test\":true}";

        String sign1 = WebhookVerificationFilter.computeHmacSha256(SECRET, payload1);
        String sign2 = WebhookVerificationFilter.computeHmacSha256(SECRET, payload2);

        assertThat(sign1).isEqualTo(sign2);
    }

    @Test
    @DisplayName("computeHmacSha256：不同输入产生不同签名")
    void shouldProduceDifferentSignatureForDifferentInput() {
        String payload1 = "1234567890" + "nonce-abc" + "{\"test\":true}";
        String payload2 = "1234567890" + "nonce-xyz" + "{\"test\":true}";

        String sign1 = WebhookVerificationFilter.computeHmacSha256(SECRET, payload1);
        String sign2 = WebhookVerificationFilter.computeHmacSha256(SECRET, payload2);

        assertThat(sign1).isNotEqualTo(sign2);
    }

    @Test
    @DisplayName("computeHmacSha256：签名长度为正且为 Base64")
    void shouldProduceValidBase64Signature() {
        String sign = WebhookVerificationFilter.computeHmacSha256(SECRET, "test");

        assertThat(sign).isNotEmpty();
        assertThat(sign).matches("^[A-Za-z0-9+/]+=*$");
    }

    // ==================== WebhookSignatureTool 工具类 ====================

    @Test
    @DisplayName("WebhookSignatureTool：生成合法签名头")
    void shouldGenerateValidHeaders() {
        String body = createRequestBody("evt-tool-001");
        WebhookSignatureTool.SignedHeaders headers = WebhookSignatureTool.generateHeaders(SECRET, body);

        assertThat(headers.xSign()).isNotEmpty();
        assertThat(headers.xTimestamp()).isNotEmpty();
        assertThat(headers.xNonce()).isNotEmpty();
        assertThat(headers.xNonce().length()).isGreaterThanOrEqualTo(16);

        String recomputed = WebhookSignatureTool.computeSignature(
                SECRET, Long.parseLong(headers.xTimestamp()), headers.xNonce(), body);
        assertThat(recomputed).isEqualTo(headers.xSign());
    }

    @Test
    @DisplayName("WebhookSignatureTool：指定参数生成签名")
    void shouldGenerateWithSpecifiedParams() {
        String body = createRequestBody("evt-tool-002");
        long timestamp = 1752700800000L;
        String nonce = "test-nonce-12345";

        WebhookSignatureTool.SignedHeaders headers = WebhookSignatureTool.generateHeaders(
                SECRET, timestamp, nonce, body);

        assertThat(headers.xTimestamp()).isEqualTo("1752700800000");
        assertThat(headers.xNonce()).isEqualTo("test-nonce-12345");
    }
}
