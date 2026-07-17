package com.jushan.platform.modules.device.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.framework.web.CachedBodyHttpServletRequest;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.WebhookSecret;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.WebhookSecretMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Device Access Webhook 签名校验过滤器。
 * <p>
 * 对 {@code /api/v1/device-webhook/**} 路径的请求执行 HMAC-SHA256 签名校验：
 * <ul>
 *   <li>提取 X-Sign、X-Timestamp、X-Nonce 请求头</li>
 *   <li>校验时间戳偏差（±5 分钟）</li>
 *   <li>校验 nonce 防重放（Redis SETNX，TTL 10 分钟）</li>
 *   <li>计算 HMAC-SHA256(secret, timestamp + nonce + rawBody)</li>
 *   <li>与 X-Sign 比对，不匹配返回 401</li>
 * </ul>
 * <p>
 * <b>安全约束</b>：校验失败仅返回 401，不返回具体原因细节（防探测）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public class WebhookVerificationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookVerificationFilter.class);

    static final String WEBHOOK_PATH_PREFIX = "/api/v1/device-webhook";

    static final String HEADER_SIGN = "X-Sign";
    static final String HEADER_TIMESTAMP = "X-Timestamp";
    static final String HEADER_NONCE = "X-Nonce";

    static final Duration TIMESTAMP_TOLERANCE = Duration.ofMinutes(5);
    static final Duration NONCE_TTL = Duration.ofMinutes(10);

    static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String NONCE_KEY_PREFIX = "webhook:nonce:";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DeviceMapper deviceMapper;

    private final WebhookSecretMapper webhookSecretMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final boolean signatureEnabled;

    public WebhookVerificationFilter(DeviceMapper deviceMapper,
                                     WebhookSecretMapper webhookSecretMapper,
                                     ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                     boolean signatureEnabled) {
        this.deviceMapper = deviceMapper;
        this.webhookSecretMapper = webhookSecretMapper;
        this.stringRedisTemplate = redisTemplateProvider.getIfAvailable();
        this.signatureEnabled = signatureEnabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith(WEBHOOK_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (!signatureEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest;
        if (request instanceof CachedBodyHttpServletRequest) {
            cachedRequest = (CachedBodyHttpServletRequest) request;
        } else {
            cachedRequest = new CachedBodyHttpServletRequest(request);
        }

        try {
            verifySignature(cachedRequest);
        } catch (WebhookVerificationException e) {
            log.warn("Webhook 签名校验失败: path={}, reason={}, clientIp={}",
                    request.getServletPath(), e.getMessage(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\"}");
            return;
        }

        try {
            filterChain.doFilter(cachedRequest, response);
        } catch (ServletException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private void verifySignature(CachedBodyHttpServletRequest request) throws WebhookVerificationException {
        String sign = request.getHeader(HEADER_SIGN);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);

        if (sign == null || sign.isBlank()) {
            throw new WebhookVerificationException("missing X-Sign header");
        }
        if (timestampStr == null || timestampStr.isBlank()) {
            throw new WebhookVerificationException("missing X-Timestamp header");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new WebhookVerificationException("missing X-Nonce header");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new WebhookVerificationException("invalid X-Timestamp format");
        }

        Instant requestTime = Instant.ofEpochMilli(timestamp);
        Instant now = Instant.now();
        Duration deviation = Duration.between(requestTime, now).abs();
        if (deviation.compareTo(TIMESTAMP_TOLERANCE) > 0) {
            throw new WebhookVerificationException("timestamp out of tolerance");
        }

        if (!isNonceValid(nonce)) {
            throw new WebhookVerificationException("nonce replay detected");
        }

        byte[] rawBody = request.getCachedBody();
        String deviceSn = extractDeviceSn(rawBody);
        if (deviceSn == null || deviceSn.isBlank()) {
            throw new WebhookVerificationException("cannot extract deviceSn from body");
        }

        Device device = deviceMapper.selectByDeviceSn(deviceSn);
        if (device == null) {
            throw new WebhookVerificationException("unknown deviceSn");
        }

        WebhookSecret webhookSecret = webhookSecretMapper.selectByParkingLotId(device.getParkingLotId());
        if (webhookSecret == null || webhookSecret.getSecret() == null || webhookSecret.getSecret().isBlank()) {
            throw new WebhookVerificationException("no secret configured for parking lot");
        }

        String payload = timestampStr + nonce + new String(rawBody, StandardCharsets.UTF_8);
        String computedSign = computeHmacSha256(webhookSecret.getSecret(), payload);

        if (!computedSign.equals(sign)) {
            throw new WebhookVerificationException("signature mismatch");
        }

        log.debug("Webhook 签名校验通过: deviceSn={}, parkingLotId={}",
                deviceSn, device.getParkingLotId());
    }

    private boolean isNonceValid(String nonce) {
        if (stringRedisTemplate == null) {
            log.warn("Redis 不可用，跳过 nonce 校验: nonce={}", nonce);
            return true;
        }
        String key = NONCE_KEY_PREFIX + nonce;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", NONCE_TTL);
        return Boolean.TRUE.equals(success);
    }

    private String extractDeviceSn(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(body, Map.class);
            Object sn = map.get("deviceSn");
            return sn != null ? sn.toString() : null;
        } catch (Exception e) {
            log.debug("解析请求体提取 deviceSn 失败: {}", e.getMessage());
            return null;
        }
    }

    static String computeHmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC 计算失败", e);
        }
    }

    static class WebhookVerificationException extends Exception {
        WebhookVerificationException(String message) {
            super(message);
        }
    }
}
