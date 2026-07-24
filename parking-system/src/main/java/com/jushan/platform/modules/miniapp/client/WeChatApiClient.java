package com.jushan.platform.modules.miniapp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.BusinessException;
import com.jushan.platform.modules.miniapp.config.WxMiniappProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 微信服务端 API 客户端。
 * <p>
 * 封装 code2session、getAccessToken、getPhoneNumber 等微信服务端接口调用。
 * access_token 通过 Redis 缓存，TTL 6900s（7200-300）。
 * mock-login 模式下 code2session 返回 mock openid。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Component
public class WeChatApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatApiClient.class);

    // ---- 微信 API 端点 ----
    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session"
                    + "?appid={appid}&secret={secret}&js_code={jsCode}&grant_type=authorization_code";

    private static final String ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token"
                    + "?grant_type=client_credential&appid={appid}&secret={secret}";

    private static final String GET_PHONE_URL =
            "https://api.weixin.qq.com/wxa/business/getuserphonenumber"
                    + "?access_token={accessToken}";

    private static final String SUBSCRIBE_MESSAGE_SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send"
                    + "?access_token={accessToken}";

    // ---- Redis 缓存键 ----
    private static final String ACCESS_TOKEN_KEY_PREFIX = "wechat:access_token:";
    private static final long ACCESS_TOKEN_TTL_SECONDS = 6900; // 7200 - 300

    // ---- 分布式锁键 ----
    private static final String TOKEN_LOCK_KEY_PREFIX = "wechat:token_lock:";
    private static final long TOKEN_LOCK_TTL_SECONDS = 10;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WxMiniappProperties props;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${wx.mock-login:false}")
    private boolean mockLoginEnabled;

    public WeChatApiClient(WxMiniappProperties props,
                           ObjectMapper objectMapper,
                           org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisProvider) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = redisProvider.getIfAvailable();

        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(props.getReadTimeout()))
                .build();

        log.info("WeChatApiClient 初始化: mockLogin={}, appid={}",
                mockLoginEnabled,
                props.getAppid() != null && !props.getAppid().isBlank()
                        ? props.getAppid().substring(0, 6) + "***" : "(未配置)");
    }

    // ==================== code2session ====================

    /**
     * 使用 jsCode 换取 openid 和 session_key。
     * <p>
     * mock 模式下返回 {@code mock_openid_ + code} 前缀。
     *
     * @param jsCode 微信登录凭证
     * @return 微信登录会话信息
     * @throws BusinessException 如果 code 无效或微信服务异常
     */
    public Code2SessionResult code2session(String jsCode) {
        if (mockLoginEnabled) {
            log.warn("[Mock] 微信 code2session: code={}", jsCode);
            Code2SessionResult result = new Code2SessionResult();
            result.setOpenid("mock_openid_" + jsCode);
            result.setSessionKey("mock_session_key_" + jsCode);
            result.setUnionid(null);
            return result;
        }

        try {
            String responseJson = restTemplate.getForObject(
                    CODE2SESSION_URL, String.class,
                    props.getAppid(), props.getSecret(), jsCode);

            JsonNode node = objectMapper.readTree(responseJson);
            Integer errcode = node.has("errcode") ? node.get("errcode").asInt() : 0;

            if (errcode != 0) {
                String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "未知错误";
                log.error("code2session 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw mapWeChatError(errcode, errmsg);
            }

            Code2SessionResult result = new Code2SessionResult();
            result.setOpenid(node.get("openid").asText());
            result.setSessionKey(node.has("session_key") ? node.get("session_key").asText() : null);
            result.setUnionid(node.has("unionid") ? node.get("unionid").asText() : null);

            log.info("code2session 成功: openid={}", maskOpenid(result.getOpenid()));
            return result;

        } catch (RestClientException e) {
            log.error("code2session 网络异常", e);
            throw new BusinessException(9999, "微信服务繁忙，请重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("code2session 解析异常", e);
            throw new BusinessException(9999, "微信服务繁忙，请重试");
        }
    }

    // ==================== access_token ====================

    /**
     * 获取小程序全局 access_token（带 Redis 缓存与分布式锁）。
     *
     * @return access_token
     */
    public String getAccessToken() {
        String cacheKey = ACCESS_TOKEN_KEY_PREFIX + props.getAppid();

        // 1. 从 Redis 读取
        if (stringRedisTemplate != null) {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        }

        // 2. 获取分布式锁（防止并发重复调微信）
        String lockKey = TOKEN_LOCK_KEY_PREFIX + props.getAppid();
        boolean locked = false;
        if (stringRedisTemplate != null) {
            locked = Boolean.TRUE.equals(
                    stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", TOKEN_LOCK_TTL_SECONDS, TimeUnit.SECONDS));
        }
        try {
            // Double-check: 获取锁后再次检查缓存
            if (stringRedisTemplate != null) {
                String doubleCheck = stringRedisTemplate.opsForValue().get(cacheKey);
                if (doubleCheck != null && !doubleCheck.isBlank()) {
                    return doubleCheck;
                }
            }

            // 3. 调微信接口获取
            String responseJson = restTemplate.getForObject(
                    ACCESS_TOKEN_URL, String.class,
                    props.getAppid(), props.getSecret());

            JsonNode node;
            try {
                node = objectMapper.readTree(responseJson);
            } catch (Exception e) {
                log.error("access_token 响应解析失败: {}", responseJson);
                throw new BusinessException(9999, "微信服务繁忙，请重试");
            }

            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                int errcode = node.get("errcode").asInt();
                String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "未知错误";
                log.error("getAccessToken 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw mapWeChatError(errcode, errmsg);
            }

            String token = node.get("access_token").asText();

            // 4. 存入 Redis
            if (stringRedisTemplate != null) {
                stringRedisTemplate.opsForValue().set(cacheKey, token, ACCESS_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
            }

            log.info("getAccessToken 成功: token={}", maskToken(token));
            return token;

        } catch (RestClientException e) {
            log.error("getAccessToken 网络异常", e);
            throw new BusinessException(9999, "微信服务繁忙，请重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("getAccessToken 异常", e);
            throw new BusinessException(9999, "微信服务繁忙，请重试");
        } finally {
            if (locked && stringRedisTemplate != null) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }

    // ==================== getPhoneNumber ====================

    /**
     * 使用前端 getPhoneNumber 返回的 code 换取手机号。
     *
     * @param code 前端 getPhoneNumber 返回的动态令牌
     * @return 手机号信息
     * @throws BusinessException 如果 code 无效或微信服务异常
     */
    public WeChatPhoneInfo getPhoneNumber(String code) {
        String accessToken = getAccessToken();

        // 构造请求体：{"code":"xxx"}
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(
                    java.util.Map.of("code", code));
        } catch (Exception e) {
            throw new BusinessException(9999, "系统异常");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String responseJson = restTemplate.postForObject(GET_PHONE_URL,
                    new HttpEntity<>(requestBody, headers),
                    String.class,
                    accessToken);

            JsonNode node = objectMapper.readTree(responseJson);

            int errcode = node.has("errcode") ? node.get("errcode").asInt() : 0;
            if (errcode != 0) {
                String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "未知错误";
                log.error("getPhoneNumber 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw mapWeChatError(errcode, errmsg);
            }

            JsonNode phoneInfo = node.get("phone_info");
            if (phoneInfo == null) {
                throw new BusinessException(9999, "微信未返回手机号信息");
            }

            WeChatPhoneInfo result = new WeChatPhoneInfo();
            result.setPurePhoneNumber(phoneInfo.has("purePhoneNumber")
                    ? phoneInfo.get("purePhoneNumber").asText() : null);
            result.setPhoneNumber(phoneInfo.has("phoneNumber")
                    ? phoneInfo.get("phoneNumber").asText() : null);
            result.setCountryCode(phoneInfo.has("countryCode")
                    ? phoneInfo.get("countryCode").asText() : "86");

            if (result.getPurePhoneNumber() == null || result.getPurePhoneNumber().isBlank()) {
                throw new BusinessException(9999, "微信未返回手机号");
            }

            log.info("getPhoneNumber 成功: phone={}", maskPhone(result.getPurePhoneNumber()));
            return result;

        } catch (RestClientException e) {
            log.error("getPhoneNumber 网络异常", e);
            throw new BusinessException(9999, "微信服务繁忙，请重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("getPhoneNumber 解析异常", e);
            throw new BusinessException(9999, "微信服务繁忙，请重试");
        }
    }

    // ==================== sendSubscribeMessage ====================

    /**
     * 发送微信订阅消息。
     * <p>
     * 调用 {@code POST https://api.weixin.qq.com/cgi-bin/message/subscribe/send}。
     * 错误码 43101（用户拒收）/ 41030（模板不存在）仅记录警告日志，不抛异常。
     *
     * @param openid     接收者 openid
     * @param templateId 订阅消息模板 ID
     * @param data       模板数据，key 为模板字段名，value 为该字段值的 Map
     * @return true 表示发送成功；用户拒收或模板不存在时返回 false
     * @throws BusinessException 网络异常或微信服务内部错误
     */
    public boolean sendSubscribeMessage(String openid, String templateId,
                                         java.util.Map<String, Object> data) {
        if (openid == null || openid.isBlank() || templateId == null || templateId.isBlank()) {
            log.warn("sendSubscribeMessage 参数不完整: openid={} templateId={}", openid, templateId);
            return false;
        }

        String accessToken = getAccessToken();

        // 构造请求体: {"touser":"OPENID","template_id":"TEMPLATE_ID","data":{...}}
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        body.put("data", data);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String requestBody = objectMapper.writeValueAsString(body);

            String responseJson = restTemplate.postForObject(
                    SUBSCRIBE_MESSAGE_SEND_URL,
                    new HttpEntity<>(requestBody, headers),
                    String.class,
                    accessToken);

            JsonNode node = objectMapper.readTree(responseJson);
            int errcode = node.has("errcode") ? node.get("errcode").asInt() : 0;

            if (errcode == 0) {
                log.info("订阅消息发送成功: openid={} templateId={}", maskOpenid(openid), templateId);
                return true;
            }

            String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "未知错误";

            // 43101: 用户拒收 / 41030: 模板不存在 → 仅 warn，不抛异常
            if (errcode == 43101 || errcode == 41030) {
                log.warn("订阅消息发送被拒绝: errcode={} errmsg={} openid={} templateId={}",
                        errcode, errmsg, maskOpenid(openid), templateId);
                return false;
            }

            log.error("订阅消息发送失败: errcode={} errmsg={} openid={} templateId={}",
                    errcode, errmsg, maskOpenid(openid), templateId);
            return false;

        } catch (RestClientException e) {
            log.error("订阅消息发送网络异常: openid={}", maskOpenid(openid), e);
            return false;
        } catch (Exception e) {
            log.error("订阅消息发送异常: openid={}", maskOpenid(openid), e);
            return false;
        }
    }

    // ==================== 错误映射 ====================

    /**
     * 微信错误码映射为业务异常。
     */
    private BusinessException mapWeChatError(int errcode, String errmsg) {
        return switch (errcode) {
            case 40029, 40163 -> new BusinessException(1002, "登录凭证已失效，请重新授权");
            case 45011 -> new BusinessException(1002, "操作太频繁，请稍后再试");
            case 40013 -> new BusinessException(1002, "小程序 AppId 配置错误");
            case -1 -> new BusinessException(9999, "微信服务繁忙，请重试");
            default -> new BusinessException(9999, "微信服务异常: " + errmsg);
        };
    }

    // ==================== 脱敏工具 ====================

    private static String maskOpenid(String openid) {
        if (openid == null || openid.length() < 8) return openid;
        return openid.substring(0, 4) + "***" + openid.substring(openid.length() - 4);
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 16) return token;
        return token.substring(0, 8) + "***" + token.substring(token.length() - 8);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ==================== 内部类型 ====================

    /**
     * code2session 返回结果。
     */
    public static class Code2SessionResult {
        private String openid;
        private String sessionKey;
        private String unionid;

        public String getOpenid() { return openid; }
        public void setOpenid(String openid) { this.openid = openid; }
        public String getSessionKey() { return sessionKey; }
        public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
        public String getUnionid() { return unionid; }
        public void setUnionid(String unionid) { this.unionid = unionid; }
    }

    /**
     * getPhoneNumber 返回结果。
     */
    public static class WeChatPhoneInfo {
        private String purePhoneNumber;
        private String phoneNumber;
        private String countryCode;

        public String getPurePhoneNumber() { return purePhoneNumber; }
        public void setPurePhoneNumber(String purePhoneNumber) { this.purePhoneNumber = purePhoneNumber; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    }
}
