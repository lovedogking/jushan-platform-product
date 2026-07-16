package com.jushan.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.system.entity.PayMerchantConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * P云支付平台客户端（Sprint 8）。
 * <p>
 * <strong>已废弃（Phase 0 S0-3）</strong>：系统已切换为模拟支付模式，
 * 不再连接真实支付平台（4pyun.com）。此类保留仅作参考，
 * 生产环境不会调用真实接口。
 * <p>
 * 所有支付流程请使用 {@link com.jushan.system.service.MockPaymentService}。
 * <p>
 * 封装 P云开放平台支付接口：
 * <ol>
 *   <li>被扫交易请求 /gate/1.0/payment/trade/create</li>
 *   <li>交易预请求 /gate/1.0/payment/trade/prepare</li>
 *   <li>查询交易状态 /gate/1.0/payment/trade/query</li>
 *   <li>退款 /gate/1.0/payment/trade/refund</li>
 * </ol>
 * <p>
 * 签名规则：
 * <ul>
 *   <li>Form 表单：参数按 key 升序拼接 + app_secret，MD5 签名</li>
 *   <li>JSON 传参：JSON 字符串 + &app_secret=xxx，MD5 签名放 Authorization 头</li>
 * </ul>
 * <p>
 * <strong>Mock 模式</strong>：配置 {@code pyun.mock=true} 可启用本地 mock 支付，
 * 预请求直接返回成功，不调用真实 P云接口，用于本地开发联调。
 *
 * @author Jushan Platform
 * @deprecated 请使用 MockPaymentService。此类保留仅供架构参考，不会在生产路径中被调用。
 */
@Deprecated
@Service
public class PyunPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PyunPaymentClient.class);

    private static final String BASE_URL = "https://papi.4pyun.com";
    private static final String TRADE_CREATE = "/gate/1.0/payment/trade/create";
    private static final String TRADE_PREPARE = "/gate/1.0/payment/trade/prepare";
    private static final String TRADE_QUERY = "/gate/1.0/payment/trade/query";
    private static final String TRADE_REFUND = "/gate/1.0/payment/trade/refund";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${pyun.mock:false}")
    private boolean mockEnabled;

    public PyunPaymentClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 被扫交易请求（岗亭扫码枪）。
     *
     * @param config   商户配置
     * @param params   业务参数
     * @return P云响应 JSON
     */
    public JsonNode tradeCreate(PayMerchantConfig config, Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.put("app_id", config.getAppId());
        sorted.put("merchant", config.getMerchantNo());
        String sign = signForm(sorted, config.getAppSecret());
        sorted.put("sign", sign);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        sorted.forEach(form::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        String url = BASE_URL + TRADE_CREATE;
        log.info("P云被扫交易请求: pay_order={}", params.get("pay_order"));
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return parseResponse(response);
    }

    /**
     * 交易预请求（小程序/H5 支付）。
     *
     * @param config 商户配置
     * @param params 业务参数
     * @return P云响应 JSON
     */
    public JsonNode tradePrepare(PayMerchantConfig config, Map<String, String> params) {
        // Mock 模式：直接返回成功响应，不调用真实 P云接口
        if (mockEnabled) {
            log.info("[MOCK] P云交易预请求: pay_order={}, fee={}",
                    params.get("pay_order"), params.get("value"));
            return objectMapper.createObjectNode()
                    .put("code", "1000")
                    .put("message", "mock success")
                    .put("pay_serial", "MOCK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .put("pay_order", params.get("pay_order"))
                    .put("status", "PAYING");
        }

        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.put("app_id", config.getAppId());
        sorted.put("merchant", config.getMerchantNo());
        String sign = signForm(sorted, config.getAppSecret());
        sorted.put("sign", sign);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        sorted.forEach(form::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        String url = BASE_URL + TRADE_PREPARE;
        log.info("P云交易预请求: pay_order={}", params.get("pay_order"));
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return parseResponse(response);
    }

    /**
     * 查询交易状态。
     *
     * @param config   商户配置
     * @param payOrder 支付订单号
     * @return P云响应 JSON
     */
    public JsonNode tradeQuery(PayMerchantConfig config, String payOrder) {
        TreeMap<String, String> sorted = new TreeMap<>();
        sorted.put("app_id", config.getAppId());
        sorted.put("merchant", config.getMerchantNo());
        sorted.put("pay_order", payOrder);
        String sign = signForm(sorted, config.getAppSecret());

        StringBuilder urlBuilder = new StringBuilder(BASE_URL + TRADE_QUERY + "?");
        sorted.forEach((k, v) -> urlBuilder.append(k).append("=").append(v).append("&"));
        urlBuilder.append("sign=").append(sign);

        log.info("P云交易查询: pay_order={}", payOrder);
        ResponseEntity<String> response = restTemplate.getForEntity(urlBuilder.toString(), String.class);
        return parseResponse(response);
    }

    /**
     * 发起退款（JSON 传参 + Authorization 头签名）。
     *
     * @param config 商户配置
     * @param params 业务参数（JSON 格式）
     * @return P云响应 JSON
     */
    public JsonNode tradeRefund(PayMerchantConfig config, Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.put("app_id", config.getAppId());
        sorted.put("merchant", config.getMerchantNo());

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(sorted);
        } catch (Exception e) {
            throw new RuntimeException("退款请求 JSON 序列化失败", e);
        }

        String sign = jsonBody + "&app_secret=" + config.getAppSecret();
        String md5Sign = md5(sign);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", md5Sign);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        String url = BASE_URL + TRADE_REFUND;
        log.info("P云退款请求: order={}", params.get("order"));
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return parseResponse(response);
    }

    /**
     * 验证 P云回调签名（Form 表单格式）。
     *
     * @param params    回调参数（含 sign）
     * @param appSecret 应用密钥
     * @return 是否验证通过
     */
    public boolean verifyNotifySign(Map<String, String> params, String appSecret) {
        String receivedSign = params.remove("sign");
        if (receivedSign == null || receivedSign.isEmpty()) {
            return false;
        }
        String computed = signForm(new TreeMap<>(params), appSecret);
        return computed.equalsIgnoreCase(receivedSign);
    }

    /**
     * 通用 Form 表单 POST（P云内部接口使用）。
     * <p>
     * <strong>已废弃</strong>：系统已切换为模拟支付模式（mockEnabled 始终为 true）。
     * 当 mockEnabled=true 时直接返回模拟成功响应，不调用真实接口。
     *
     * @param url    完整 URL
     * @param config 商户配置
     * @param params 业务参数
     * @return P云响应 JSON
     */
    @Deprecated
    public JsonNode postForm(String url, PayMerchantConfig config, Map<String, String> params) {
        // Mock 模式：直接返回成功响应，不调用真实 P云接口
        if (mockEnabled) {
            log.info("[MOCK] P云表单请求已跳过: url={}", url);
            return objectMapper.createObjectNode()
                    .put("code", "1001")
                    .put("message", "mock success — parking sync skipped in mock mode");
        }
        TreeMap<String, String> sorted = new TreeMap<>(params);
        if (config.getAppId() != null) {
            sorted.put("app_id", config.getAppId());
        }
        String sign = signForm(sorted, config.getAppSecret());
        sorted.put("sign", sign);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        sorted.forEach(form::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        log.info("P云 Form 请求: url={}", url);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        return parseResponse(response);
    }

    // ==================== 内部方法 ====================

    private String signForm(TreeMap<String, String> params, String appSecret) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                sb.append(k).append("=").append(v).append("&");
            }
        });
        sb.append("app_secret=").append(appSecret);
        return md5(sb.toString());
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }

    private JsonNode parseResponse(ResponseEntity<String> response) {
        try {
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            }
            log.warn("P云响应非 2xx: status={}", response.getStatusCode());
            return objectMapper.createObjectNode()
                    .put("code", String.valueOf(response.getStatusCode().value()))
                    .put("message", "HTTP 错误");
        } catch (Exception e) {
            log.error("P云响应解析失败", e);
            return objectMapper.createObjectNode()
                    .put("code", "9001")
                    .put("message", "响应解析失败");
        }
    }

    /**
     * 生成 UTC 过期时间字符串。
     */
    public static String formatExpireTime(int minutesFromNow) {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .plusMinutes(minutesFromNow)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }
}
