# 小程序登录链路与手机号绑定 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement real WeChat mini-program login (code2session), phone binding (getPhoneNumber), JWT audience separation, and phone-bound gating on core frontend pages.

**Architecture:** New `MiniAuthController` + `MiniAuthService` on `/api/v1/mini/*`; new `WeChatApiClient` calls WeChat APIs; existing `/wx/*` paths marked `@Deprecated` with backward-compatible login delegation. Frontend `app.js` gains `onLaunch` login flow, `request.js` gains 401 auto-refresh, and a new `bind-phone` page gates core features.

**Tech Stack:** Java 21, Spring Boot 3.x, MyBatis-Plus, JWT (jjwt 0.12.x), Redis (StringRedisTemplate), WeChat mini-program APIs, RestTemplate.

---

## Phase 0: Configuration & Infrastructure

### Task 1: Add `wx.miniapp` configuration to `application.yml`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-boot/src/main/resources/application.yml`

- [ ] **Step 1: Add wx.miniapp config block**

After line 128 (`jwt.refresh-expiration` block), add:

```yaml
# ---- 微信小程序配置 ----
# appid/secret 通过环境变量注入，不入库、不入仓
wx:
  miniapp:
    appid: ${WX_MINIAPP_APPID:}
    secret: ${WX_MINIAPP_SECRET:}
    connect-timeout: 5000
    read-timeout: 10000
  mock-login: false
```

- [ ] **Step 2: Verify YAML is valid**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && python3 -c "import yaml; yaml.safe_load(open('parking-boot/src/main/resources/application.yml'))" && echo "YAML valid"
```

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-boot/src/main/resources/application.yml && git commit -m "[MINI-001] config: add wx.miniapp configuration section"
```

---

### Task 2: Add `wx.miniapp` configuration to `application-prod.yml`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-boot/src/main/resources/application-prod.yml`

- [ ] **Step 1: Add wx.miniapp block to prod profile**

After line 86 (`logging.level` section), add:

```yaml
# ---- 微信小程序（prod 强制环境变量注入，不设置默认值） ----
wx:
  miniapp:
    appid: ${WX_MINIAPP_APPID}
    secret: ${WX_MINIAPP_SECRET}
    connect-timeout: 5000
    read-timeout: 10000
  mock-login: false
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-boot/src/main/resources/application-prod.yml && git commit -m "[MINI-001] config: add wx.miniapp to prod profile"
```

---

### Task 3: Create `WxMiniappProperties.java`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/config/WxMiniappProperties.java`

- [ ] **Step 1: Create the configuration properties class**

```java
package com.jushan.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置属性。
 * <p>
 * appid/secret 通过环境变量注入，不入库、不入仓、不入版本管理。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxMiniappProperties {

    /** 小程序 AppId */
    private String appid;

    /** 小程序 Secret（必须通过环境变量注入） */
    private String secret;

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时（毫秒） */
    private int readTimeout = 10000;

    // ==================== getter / setter ====================

    public String getAppid() { return appid; }
    public void setAppid(String appid) { this.appid = appid; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
}
```

- [ ] **Step 2: Verify the file compiles by checking syntax**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/config/WxMiniappProperties.java && git commit -m "[MINI-001] feat: add WxMiniappProperties configuration class"
```

---

### Task 4: Create `WxMiniappConfig.java` with prod mock-protection

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/config/WxMiniappConfig.java`

- [ ] **Step 1: Create the config class with prod validation**

```java
package com.jushan.system.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 微信小程序配置校验。
 * <p>
 * production 环境禁止启用 mock-login。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Configuration
@Profile("prod")
public class WxMiniappConfig {

    private static final Logger log = LoggerFactory.getLogger(WxMiniappConfig.class);

    @Value("${wx.mock-login:false}")
    private boolean mockLoginEnabled;

    @PostConstruct
    public void validate() {
        if (mockLoginEnabled) {
            throw new IllegalStateException(
                    "wx.mock-login=true is not allowed in production profile. "
                            + "Set wx.mock-login=false in production environment.");
        }
        log.info("微信小程序 Prod 保护生效: mock-login={}", mockLoginEnabled);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/config/WxMiniappConfig.java && git commit -m "[MINI-001] feat: add prod mock-login protection"
```

---

### Task 5: Update `SecurityConfig.java` — permitAll new paths

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-infrastructure/src/main/java/com/jushan/platform/infra/security/SecurityConfig.java`

- [ ] **Step 1: Add new permitAll rules**

Edit the `.requestMatchers(...).permitAll()` chain. Add these lines after the existing `/wx/login` line (line 48):

```java
                // 小程序认证接口（无需登录）
                .requestMatchers("/api/v1/mini/login", "/api/v1/mini/phone").permitAll()
```

The full `authorizeHttpRequests` block should now include both `/wx/login` and the new mini paths:

```java
            .authorizeHttpRequests(auth -> auth
                // 放行登录、验证码等公开接口
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/captcha").permitAll()
                // 微信登录与注册
                .requestMatchers("/wx/login").permitAll()
                // 小程序认证接口（无需登录）
                .requestMatchers("/api/v1/mini/login", "/api/v1/mini/phone").permitAll()
                .requestMatchers("/register", "/register/**").permitAll()
                .requestMatchers("/api/v1/register").permitAll()
                // 演示与内部 mock 接口
                .requestMatchers("/demo/**").permitAll()
                .requestMatchers("/api/v1/internal/mock/**").permitAll()
                // 岗亭端登录转发（booth-web 通过 /auth/login 访问）
                .requestMatchers("/auth/login").permitAll()
                // Device Access Webhook 接收端（Device Access 系统调用，无需用户认证）
                .requestMatchers("/api/v1/device-webhook/**").permitAll()
                // 支付回调接口（外部系统调用，无需认证）
                .requestMatchers("/api/v1/orders/notify", "/api/v1/pay/notify", "/api/v1/pay/callback").permitAll()
                // 健康检查与静态资源
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/favicon.ico", "/error").permitAll()
                // springdoc-openapi / Swagger UI 文档
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // SockJS 信息端点公开（实际握手仍需认证）
                .requestMatchers("/ws/info").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
            )
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-infrastructure -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-infrastructure/src/main/java/com/jushan/platform/infra/security/SecurityConfig.java && git commit -m "[MINI-001] feat: permitAll /api/v1/mini/login and /api/v1/mini/phone"
```

---

### Task 6: Add audience overload to `JwtUtils.java`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-infrastructure/src/main/java/com/jushan/platform/infra/security/JwtUtils.java`

- [ ] **Step 1: Add new overload with audience parameter**

After the existing `generateToken` method (after line 52), add:

```java
    /**
     * 生成 JWT Token（含 audience）。
     *
     * @param userId     用户ID
     * @param tenantId   租户ID（平台用户为 null）
     * @param userType   用户类型（platform/tenant/wx_user）
     * @param roles      角色JSON字符串
     * @param permissions 权限编码列表（逗号分隔）
     * @param audience   JWT audience（如 "miniapp"、"web"），可为 null
     * @param secretKey  密钥
     * @param expiration 过期时间（毫秒）
     * @return JWT Token
     */
    public static String generateToken(Long userId, Long tenantId, String userType, String roles,
                                       String permissions, String audience, String secretKey, long expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("userType", userType)
                .claim("roles", roles)
                .claim("permissions", permissions);

        if (audience != null && !audience.isBlank()) {
            builder.audience().add(audience);
        }

        return builder
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }
```

- [ ] **Step 2: Add audience extraction method to JwtUtils**

After the `getPermissionsFromClaims` method (after line 136), add:

```java
    /**
     * 从 Claims 中提取 audience。
     *
     * @param claims Claims
     * @return audience 字符串（可能为 null）
     */
    public static String getAudienceFromClaims(Claims claims) {
        Object aud = claims.get("aud");
        if (aud == null) {
            return null;
        }
        if (aud instanceof String) {
            return (String) aud;
        }
        // jjwt 0.12.x 可能返回 List
        if (aud instanceof java.util.List) {
            var list = (java.util.List<?>) aud;
            return list.isEmpty() ? null : String.valueOf(list.get(0));
        }
        return aud.toString();
    }
```

- [ ] **Step 3: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-infrastructure -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-infrastructure/src/main/java/com/jushan/platform/infra/security/JwtUtils.java && git commit -m "[MINI-001] feat: add audience parameter to JwtUtils.generateToken"
```

---

## Phase 1: Data Layer & Response Model

### Task 7: Flyway migration — add `session_key` to `wx_user`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-boot/src/main/resources/db/migration/V20260718002__add_session_key_to_wx_user.sql`

- [ ] **Step 1: Create the Flyway migration file**

```sql
-- =============================================================================
-- Flyway 迁移：wx_user 表新增 session_key 字段
-- =============================================================================
-- 微信 session_key 用于旧版 getPhoneNumber 解密（可选）。
-- 仅当表结构无此字段时执行 ALTER。
-- =============================================================================

-- 检查字段是否已存在（Flyway 幂等：使用存储过程安全执行）
-- 如果字段已存在，MySQL 会抛出 1060 Duplicate column，使用条件判断避免
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'wx_user'
  AND COLUMN_NAME = 'session_key';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE wx_user ADD COLUMN session_key VARCHAR(100) COMMENT ''微信会话密钥'' AFTER phone',
    'SELECT ''session_key column already exists, skipping'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: Verify Flyway migration runs (dry-run check)**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn flyway:info -pl parking-boot -am 2>&1 | grep V20260718002
```

Expected: outputs migration info for V20260718002 without errors.

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-boot/src/main/resources/db/migration/V20260718002__add_session_key_to_wx_user.sql && git commit -m "[MINI-001] db: add session_key column to wx_user"
```

---

### Task 7a: Add `sessionKey` field to `WxUser.java` entity

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/entity/WxUser.java`

- [ ] **Step 1: Add sessionKey field**

After line 42 (`private String phone;`), add:

```java
    /** 微信会话密钥（用于旧版 getPhoneNumber 解密，可选） */
    private String sessionKey;
```

- [ ] **Step 2: Add getter/setter**

After the `getPhone()`/`setPhone()` methods (around line 82), add:

```java
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
```

- [ ] **Step 3: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/entity/WxUser.java && git commit -m "[MINI-001] feat: add sessionKey field to WxUser entity"
```

---

### Task 8: Add `phoneBound` to `WxLoginResult.java`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/vo/WxLoginResult.java`

- [ ] **Step 1: Add phoneBound field**

Add after line 30 (`private String message;`):

```java
    /** 手机号是否已绑定 */
    private Boolean phoneBound;
```

- [ ] **Step 2: Add getter/setter**

Add after line 57 (`setMessage` method):

```java
    public Boolean getPhoneBound() { return phoneBound; }
    public void setPhoneBound(Boolean phoneBound) { this.phoneBound = phoneBound; }
```

- [ ] **Step 3: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/vo/WxLoginResult.java && git commit -m "[MINI-001] feat: add phoneBound field to WxLoginResult"
```

---

## Phase 2: WeChat API Client

### Task 9: Create `WeChatApiClient.java` — core HTTP client and `code2session`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/client/WeChatApiClient.java`

- [ ] **Step 1: Create the WeChatApiClient class with RestTemplate + code2session**

```java
package com.jushan.system.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.BusinessException;
import com.jushan.system.config.WxMiniappProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
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
            String responseJson = restTemplate.postForObject(GET_PHONE_URL,
                    new org.springframework.http.HttpEntity<>(requestBody,
                            org.springframework.http.HttpHeaders.builder()
                                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                    .build()),
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/client/WeChatApiClient.java && git commit -m "[MINI-001] feat: add WeChatApiClient for code2session/getPhoneNumber"
```

---

## Phase 3: DTOs and VO

### Task 10: Create `MiniLoginRequest.java`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/dto/MiniLoginRequest.java`

- [ ] **Step 1: Create the DTO**

```java
package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 小程序登录请求 DTO。
 * <p>
 * 对应 POST /api/v1/mini/login。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class MiniLoginRequest {

    /** 微信登录凭证 code（通过 wx.login() 获取） */
    @NotBlank(message = "登录凭证不能为空")
    private String code;

    /** 昵称（可选，微信授权获取） */
    private String nickname;

    /** 头像 URL（可选，微信授权获取） */
    private String avatarUrl;

    // ==================== getter / setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/dto/MiniLoginRequest.java && git commit -m "[MINI-001] feat: add MiniLoginRequest DTO"
```

---

### Task 11: Create `MiniPhoneRequest.java`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/dto/MiniPhoneRequest.java`

- [ ] **Step 1: Create the DTO**

```java
package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 手机号绑定请求 DTO。
 * <p>
 * 对应 POST /api/v1/mini/phone。
 * code 为 getPhoneNumber 返回的动态令牌。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class MiniPhoneRequest {

    /** getPhoneNumber 返回的动态令牌 */
    @NotBlank(message = "手机号凭证不能为空")
    private String code;

    /** 加密向量（可选，旧版基础库兼容） */
    private String iv;

    /** 加密数据（可选，旧版基础库兼容） */
    private String encryptedData;

    // ==================== getter / setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public String getEncryptedData() { return encryptedData; }
    public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/dto/MiniPhoneRequest.java && git commit -m "[MINI-001] feat: add MiniPhoneRequest DTO"
```

---

### Task 12: Create `BindPhoneResult.java`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/vo/BindPhoneResult.java`

- [ ] **Step 1: Create the VO**

```java
package com.jushan.system.vo;

/**
 * 手机号绑定结果 VO。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class BindPhoneResult {

    /** 绑定后的脱敏手机号 */
    private String phone;

    /** 是否绑定成功 */
    private Boolean success;

    // ==================== getter / setter ====================

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/vo/BindPhoneResult.java && git commit -m "[MINI-001] feat: add BindPhoneResult VO"
```

---

## Phase 4: Service Layer

### Task 13: Create `MiniAuthService.java` — login method

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/service/MiniAuthService.java`

- [ ] **Step 1: Create the service with login() method**

```java
package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.JwtUtils;
import com.jushan.system.client.WeChatApiClient;
import com.jushan.system.dto.MiniLoginRequest;
import com.jushan.system.dto.MiniPhoneRequest;
import com.jushan.system.entity.PlateBinding;
import com.jushan.system.entity.WxUser;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.WxUserMapper;
import com.jushan.system.vo.BindPhoneResult;
import com.jushan.system.vo.WxLoginResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 小程序认证服务。
 * <p>
 * 负责微信登录、手机号绑定等认证相关逻辑。
 * 替代旧版 WxUserService 中的 mock-only 登录实现。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class MiniAuthService {

    private static final Logger log = LoggerFactory.getLogger(MiniAuthService.class);

    private static final String LOGIN_TYPE_WX = "wx_user";
    private static final String AUDIENCE_MINIAPP = "miniapp";

    private final WxUserMapper wxUserMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final WeChatApiClient weChatApiClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public MiniAuthService(WxUserMapper wxUserMapper,
                           PlateBindingMapper plateBindingMapper,
                           WeChatApiClient weChatApiClient) {
        this.wxUserMapper = wxUserMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.weChatApiClient = weChatApiClient;
    }

    /**
     * 小程序登录。
     * <p>
     * 完整链路：code → code2session → 查/建 wx_user → 签发 JWT(uid=miniapp)。
     *
     * @param request 登录请求
     * @return 登录结果（含 token 和 phoneBound）
     */
    @Transactional
    public WxLoginResult login(MiniLoginRequest request) {
        String code = request.getCode();

        // 1. 获取 openid（通过 WeChatApiClient，支持 mock）
        String openid = weChatApiClient.code2session(code).getOpenid();

        // 2. 查询或创建用户
        WxUser wxUser = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUser>().eq(WxUser::getOpenid, openid));

        boolean isNewUser = (wxUser == null);
        if (wxUser == null) {
            wxUser = createWxUser(openid, request);
        } else {
            updateLoginInfo(wxUser, request);
        }

        // 3. JWT 登录（audience=miniapp）
        String rolesJson = "[\"" + LOGIN_TYPE_WX + "\"]";
        String token = JwtUtils.generateToken(
                wxUser.getId(), null, LOGIN_TYPE_WX,
                rolesJson, null, AUDIENCE_MINIAPP, jwtSecret, jwtExpiration);

        // 4. 查询绑定车牌数量
        long plateCount = plateBindingMapper.selectCount(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, wxUser.getId())
                        .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED));

        // 5. 构建返回
        WxLoginResult result = new WxLoginResult();
        result.setToken(token);
        result.setUserId(wxUser.getId());
        result.setNickname(wxUser.getMaskedNickname());
        result.setAvatarUrl(wxUser.getAvatarUrl());
        result.setIsNewUser(isNewUser);
        result.setPhoneBound(Boolean.TRUE.equals(wxUser.getPhoneVerified()));
        result.setPlateCount((int) plateCount);
        result.setLoginTime(LocalDateTime.now());
        result.setMessage(isNewUser ? "欢迎首次使用" : "欢迎回来");

        log.info("小程序用户 {} 登录成功, isNew={}, phoneBound={}, plateCount={}",
                wxUser.getMaskedNickname(), isNewUser, result.getPhoneBound(), plateCount);

        return result;
    }

    /**
     * 创建微信用户。
     */
    private WxUser createWxUser(String openid, MiniLoginRequest request) {
        WxUser wxUser = new WxUser();
        wxUser.setOpenid(openid);
        wxUser.setNickname(request.getNickname() != null ? request.getNickname() : "");
        wxUser.setAvatarUrl(request.getAvatarUrl() != null ? request.getAvatarUrl() : "");
        wxUser.setPhoneVerified(false);
        wxUser.setStatus("ACTIVE");
        wxUser.setLastLoginAt(LocalDateTime.now());
        wxUser.setCreatedAt(LocalDateTime.now());
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.insert(wxUser);
        return wxUser;
    }

    /**
     * 更新登录信息。
     */
    private void updateLoginInfo(WxUser wxUser, MiniLoginRequest request) {
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            wxUser.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
            wxUser.setAvatarUrl(request.getAvatarUrl());
        }
        wxUser.setLastLoginAt(LocalDateTime.now());
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.updateById(wxUser);
    }

    // ---- phone binding will be added in next task ----
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/service/MiniAuthService.java && git commit -m "[MINI-001] feat: add MiniAuthService.login()"
```

---

### Task 14: Add `bindPhone()` to `MiniAuthService.java`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/service/MiniAuthService.java`

- [ ] **Step 1: Add bindPhone() method before the class closing brace**

Insert the following code immediately before the final `}` of the class (after the `updateLoginInfo` method and before the end of the class):

```java
    /**
     * 绑定手机号（通过微信 getPhoneNumber）。
     * <p>
     * 完整链路：getPhoneNumber(code) → 校验唯一性 → 更新 wx_user.phone / phone_verified。
     *
     * @param request 绑定请求（含 getPhoneNumber 返回的 code）
     * @return 绑定结果
     */
    @Transactional
    public BindPhoneResult bindPhone(MiniPhoneRequest request) {
        Long userId = TenantContext.requireUserId();

        // 1. 调微信获取手机号
        WeChatApiClient.WeChatPhoneInfo phoneInfo =
                weChatApiClient.getPhoneNumber(request.getCode());

        String purePhone = phoneInfo.getPurePhoneNumber();

        // 2. 校验手机号唯一性
        WxUser existing = wxUserMapper.selectOne(
                new LambdaQueryWrapper<WxUser>()
                        .eq(WxUser::getPhone, purePhone)
                        .ne(WxUser::getId, userId));
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该手机号已被其他账号绑定");
        }

        // 3. 更新用户
        WxUser wxUser = wxUserMapper.selectById(userId);
        if (wxUser == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "用户不存在");
        }
        wxUser.setPhone(purePhone);
        wxUser.setPhoneVerified(true);
        wxUser.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.updateById(wxUser);

        log.info("小程序用户 {} 绑定手机号 {}", userId, wxUser.getMaskedPhone());

        BindPhoneResult result = new BindPhoneResult();
        result.setPhone(wxUser.getMaskedPhone());
        result.setSuccess(true);
        return result;
    }
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/service/MiniAuthService.java && git commit -m "[MINI-001] feat: add MiniAuthService.bindPhone()"
```

---

### Task 15: Update `WxUserService.java` — resolveOpenid with WeChatApiClient

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/service/WxUserService.java`

- [ ] **Step 1: Add WeChatApiClient import and field**

Add after line 7 (`import com.jushan.common.CommonErrorCode;`):

```java
import com.jushan.system.client.WeChatApiClient;
```

Add field after line 49 (`private final BindingPolicyMapper bindingPolicyMapper;`):

```java
    private final WeChatApiClient weChatApiClient;
```

Add constructor parameter after line 66 (`this.bindingPolicyMapper = bindingPolicyMapper;`):

```java
        this.weChatApiClient = weChatApiClient;
```

Add to the constructor signature:

```java
    public WxUserService(WxUserMapper wxUserMapper,
                         VehicleMapper vehicleMapper,
                         PlateBindingMapper plateBindingMapper,
                         BindingPolicyMapper bindingPolicyMapper,
                         WeChatApiClient weChatApiClient) {
```

- [ ] **Step 2: Replace the resolveOpenid method**

Find the `resolveOpenid` method (lines 136-147). Replace it with:

```java
    /**
     * 解析 openid。
     * <p>
     * - Mock 模式：使用 code 作为 openid 前缀
     * - 真实模式：通过 WeChatApiClient 调用微信 code2session
     */
    private String resolveOpenid(String code) {
        return weChatApiClient.code2session(code).getOpenid();
    }
```

- [ ] **Step 3: Update login() to set phoneBound**

In the `login` method, after the line `result.setIsNewUser(isNewUser);` (line 119), add:

```java
        result.setPhoneBound(Boolean.TRUE.equals(wxUser.getPhoneVerified()));
```

- [ ] **Step 4: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/service/WxUserService.java && git commit -m "[MINI-001] feat: delegate resolveOpenid to WeChatApiClient, set phoneBound"
```

---

### Task 16: Update `JwtAuthenticationFilter.java` — audience check

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-infrastructure/src/main/java/com/jushan/platform/infra/security/JwtAuthenticationFilter.java`

- [ ] **Step 1: Add audience validation BEFORE SecurityContext setup**

The audience check must happen before the SecurityContext and TenantContext are populated, so that an incorrect audience token is treated as unauthenticated (not authenticated-but-wrong-audience).

In the `doFilterInternal` method, find the block starting with `if (StringUtils.hasText(token) && JwtUtils.validateToken(token, secretKey)) {` (around line 46). Inside this block, BEFORE the `Long userId = JwtUtils.getUserIdFromClaims(claims);` line, add the audience extraction and check:

```java
            if (StringUtils.hasText(token) && JwtUtils.validateToken(token, secretKey)) {
                var claims = JwtUtils.parseClaims(token, secretKey);

                // audience 校验（在设置 SecurityContext 之前）：/api/v1/mini/** 路径要求 aud=miniapp
                String audience = JwtUtils.getAudienceFromClaims(claims);
                String requestPath = request.getRequestURI();
                if (requestPath.startsWith("/api/v1/mini/")
                        && !"miniapp".equals(audience)) {
                    log.warn("JWT audience 校验失败: path={}, aud={}, 期望=miniapp", requestPath, audience);
                    // 不设置 SecurityContext，让 SecurityConfig 的 .anyRequest().authenticated() 返回 401
                    filterChain.doFilter(request, response);
                    return;
                }
```

The audience check now sits immediately after `parseClaims` and before `getUserIdFromClaims`. If the audience check fails for a `/api/v1/mini/*` path, the filter returns early without setting SecurityContext or TenantContext, and the request is treated as unauthenticated (SecurityConfig will respond with 401).

- [ ] **Step 2: Add the `getAudienceFromClaims` import if not already present**

Verify that `JwtUtils.getAudienceFromClaims` is accessible (the method was added in Task 6). No additional import needed — it's a static method in the same package.

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-infrastructure -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-infrastructure/src/main/java/com/jushan/platform/infra/security/JwtAuthenticationFilter.java && git commit -m "[MINI-001] feat: add JWT audience validation for /api/v1/mini/**"
```

---

## Phase 5: Controllers

### Task 17: Create `MiniAuthController.java`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/controller/MiniAuthController.java`

- [ ] **Step 1: Create the controller**

```java
package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.system.dto.MiniLoginRequest;
import com.jushan.system.dto.MiniPhoneRequest;
import com.jushan.system.service.MiniAuthService;
import com.jushan.system.vo.BindPhoneResult;
import com.jushan.system.vo.WxLoginResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序认证控制器。
 * <p>
 * 提供 POST /api/v1/mini/login 和 POST /api/v1/mini/phone。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/mini")
public class MiniAuthController {

    private final MiniAuthService miniAuthService;

    public MiniAuthController(MiniAuthService miniAuthService) {
        this.miniAuthService = miniAuthService;
    }

    /**
     * 小程序登录。
     * <p>
     * 前端调用：{@code POST /api/v1/mini/login}
     * <p>
     * 通过 wx.login() 获取 code，后端使用 WeChatApiClient.code2session 换取 openid，
     * 签发 aud=miniapp 的 JWT。
     */
    @PostMapping("/login")
    public R<WxLoginResult> login(@Valid @RequestBody MiniLoginRequest request) {
        WxLoginResult result = miniAuthService.login(request);
        return R.ok(result);
    }

    /**
     * 绑定手机号。
     * <p>
     * 前端调用：{@code POST /api/v1/mini/phone}
     * <p>
     * 通过 getPhoneNumber 返回的 code 换取真实手机号并绑定。
     */
    @PostMapping("/phone")
    public R<BindPhoneResult> bindPhone(@Valid @RequestBody MiniPhoneRequest request) {
        BindPhoneResult result = miniAuthService.bindPhone(request);
        return R.ok(result);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/controller/MiniAuthController.java && git commit -m "[MINI-001] feat: add MiniAuthController for /api/v1/mini/login and /phone"
```

---

### Task 18: Update `WxUserController.java` — mark Deprecated, delegate, 410

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/controller/WxUserController.java`

- [ ] **Step 1: Add imports for MiniAuthService**

Replace line 3 with:

```java
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.system.dto.BindPhoneRequest;
import com.jushan.system.dto.BindPlateRequest;
import com.jushan.system.dto.MiniLoginRequest;
import com.jushan.system.dto.UnbindPlateRequest;
import com.jushan.system.dto.WxLoginRequest;
import com.jushan.system.service.MiniAuthService;
import com.jushan.system.service.WxUserService;
import com.jushan.system.vo.PlateBindingVo;
import com.jushan.system.vo.WxLoginResult;
import com.jushan.system.vo.WxUserVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
```

- [ ] **Step 2: Add MiniAuthService field and update constructor**

Add field after line 29:

```java
    private final MiniAuthService miniAuthService;
```

Update the constructor:

```java
    public WxUserController(WxUserService wxUserService, MiniAuthService miniAuthService) {
        this.wxUserService = wxUserService;
        this.miniAuthService = miniAuthService;
    }
```

- [ ] **Step 3: Update the /wx/login method to delegate**

Replace the `login` method (lines 43-47) with:

```java
    /**
     * 微信登录（已废弃，请使用 POST /api/v1/mini/login）。
     * <p>
     * 当前委托新 MiniAuthService 处理，保持向后兼容。
     *
     * @deprecated 请迁移至 POST /api/v1/mini/login
     */
    @PostMapping("/login")
    @Deprecated
    public R<WxLoginResult> login(@Valid @RequestBody WxLoginRequest request) {
        // 将 WxLoginRequest 转换为 MiniLoginRequest
        MiniLoginRequest miniRequest = new MiniLoginRequest();
        miniRequest.setCode(request.getCode());
        miniRequest.setNickname(request.getNickname());
        miniRequest.setAvatarUrl(request.getAvatarUrl());
        return R.ok(miniAuthService.login(miniRequest));
    }
```

- [ ] **Step 4: Update the /wx/phone method to return 410 Gone**

Replace the `bindPhone` method (lines 111-115) with:

```java
    /**
     * 绑定手机号（已废弃，请使用 POST /api/v1/mini/phone）。
     * <p>
     * 旧接口明文手机号绑定已废弃，getPhoneNumber 流程请用 /api/v1/mini/phone。
     *
     * @deprecated 请迁移至 POST /api/v1/mini/phone
     */
    @PostMapping("/phone")
    @Deprecated
    public R<Void> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        return R.fail(CommonErrorCode.METHOD_NOT_ALLOWED.getCode(),
                "此接口已废弃，请在微信小程序中更新版本");
    }
```

- [ ] **Step 5: Verify compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn compile -pl parking-system -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add parking-system/src/main/java/com/jushan/system/controller/WxUserController.java && git commit -m "[MINI-001] refactor: mark /wx/login @Deprecated, delegate to MiniAuthService; /wx/phone returns 410"
```

---

### Task 19: Full backend compilation & smoke check

- [ ] **Step 1: Run full backend compilation**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn clean compile -pl parking-system -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Run all tests**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn test -pl parking-boot -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS` (tests pass). If any pre-existing test failures exist, note them but verify no new failures.

---

## Phase 6: Frontend — Missing JSON Configuration Files

### Task 20: Create `lot-space.json`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/lot-space/lot-space.json`

- [ ] **Step 1: Create the file**

```json
{
  "usingComponents": {},
  "navigationBarTitleText": "余位查询"
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/lot-space/lot-space.json && git commit -m "[MINI-001] fix: add missing lot-space.json"
```

---

### Task 21: Create `messages.json`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/messages/messages.json`

- [ ] **Step 1: Create the file**

```json
{
  "usingComponents": {},
  "navigationBarTitleText": "消息中心"
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/messages/messages.json && git commit -m "[MINI-001] fix: add missing messages.json"
```

---

### Task 22: Create `proxy-pay.json`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/proxy-pay/proxy-pay.json`

- [ ] **Step 1: Create the file**

```json
{
  "usingComponents": {},
  "navigationBarTitleText": "代缴停车费"
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/proxy-pay/proxy-pay.json && git commit -m "[MINI-001] fix: add missing proxy-pay.json"
```

---

## Phase 7: Frontend — Bind Phone Page

### Task 23: Create `bind-phone.json`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/bind-phone/bind-phone.json`

- [ ] **Step 1: Create the file**

```json
{
  "usingComponents": {},
  "navigationBarTitleText": "绑定手机号"
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/bind-phone/bind-phone.json && git commit -m "[MINI-001] feat: add bind-phone.json"
```

---

### Task 24: Create `bind-phone.wxml`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/bind-phone/bind-phone.wxml`

- [ ] **Step 1: Create the template**

```html
<view class="bind-phone-page">
  <view class="bind-header">
    <image class="bind-icon" src="/images/phone-bind.png" mode="aspectFit" />
    <text class="bind-title">为保障服务，请绑定手机号</text>
    <text class="bind-desc">绑定后可享受停车缴费、月卡申请等服务</text>
  </view>
  <view class="bind-body">
    <button class="btn-get-phone" open-type="getPhoneNumber"
            bindgetphonenumber="onGetPhoneNumber"
            loading="{{loading}}"
            disabled="{{loading}}">
      {{loading ? '绑定中...' : '微信手机号一键绑定'}}
    </button>
    <view class="skip-bind" bindtap="onSkip">
      稍后绑定
    </view>
  </view>
  <view class="bind-footer">
    <text class="footer-text">您的手机号仅用于接收停车通知和账单</text>
  </view>
</view>
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/bind-phone/bind-phone.wxml && git commit -m "[MINI-001] feat: add bind-phone.wxml"
```

---

### Task 25: Create `bind-phone.wxss`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/bind-phone/bind-phone.wxss`

- [ ] **Step 1: Create the stylesheet**

```css
.bind-phone-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
  background: #f5f7fa;
  padding: 60rpx 40rpx;
  box-sizing: border-box;
}

.bind-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 120rpx;
  margin-bottom: 80rpx;
}

.bind-icon {
  width: 160rpx;
  height: 160rpx;
  margin-bottom: 40rpx;
}

.bind-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 16rpx;
}

.bind-desc {
  font-size: 28rpx;
  color: #87989f;
  text-align: center;
  line-height: 40rpx;
}

.bind-body {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.btn-get-phone {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  background: #1677FF;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 500;
  border-radius: 16rpx;
  border: none;
}

.btn-get-phone[disabled] {
  background: #a0c4ff;
}

.skip-bind {
  margin-top: 48rpx;
  font-size: 28rpx;
  color: #87989f;
  padding: 16rpx 32rpx;
}

.bind-footer {
  position: fixed;
  bottom: 60rpx;
  left: 0;
  right: 0;
  text-align: center;
}

.footer-text {
  font-size: 24rpx;
  color: #b0b8bf;
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/bind-phone/bind-phone.wxss && git commit -m "[MINI-001] feat: add bind-phone.wxss"
```

---

### Task 26: Create `bind-phone.js`

**Files:**
- Create: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/bind-phone/bind-phone.js`

- [ ] **Step 1: Create the page logic**

```javascript
/**
 * 手机号绑定引导页
 * 使用微信 getPhoneNumber 一键绑定
 */
const { post } = require('../../utils/request')

Page({
  data: {
    loading: false,
  },

  /**
   * getPhoneNumber 回调
   */
  async onGetPhoneNumber(e) {
    // 用户拒绝授权
    if (e.detail.errMsg && e.detail.errMsg.includes('deny')) {
      wx.showToast({ title: '需要授权手机号才能继续', icon: 'none', duration: 2000 })
      return
    }

    // 未返回 code 时跳过
    if (!e.detail.code) {
      return
    }

    this.setData({ loading: true })

    const { code, iv, encryptedData } = e.detail
    try {
      await post('/api/v1/mini/phone', { code, iv, encryptedData })
      getApp().globalData.phoneBound = true
      wx.showToast({ title: '绑定成功', icon: 'success', duration: 1500 })
      setTimeout(() => {
        wx.switchTab({ url: '/pages/index/index' })
      }, 1500)
    } catch (err) {
      wx.showModal({
        title: '绑定失败',
        content: (err && err.message) || '请重试',
        confirmText: '重试',
        cancelText: '稍后绑定',
        success: (res) => {
          if (!res.confirm) {
            wx.switchTab({ url: '/pages/index/index' })
          }
        },
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 稍后绑定 — 跳转首页
   */
  onSkip() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/bind-phone/bind-phone.js && git commit -m "[MINI-001] feat: add bind-phone.js"
```

---

## Phase 8: Frontend — Core App Files

### Task 27: Update `app.json` — register new page and permissions

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/app.json`

- [ ] **Step 1: Replace the entire app.json**

```json
{
  "pages": [
    "pages/index/index",
    "pages/profile/profile",
    "pages/plate/plate",
    "pages/records/records",
    "pages/parking/parking",
    "pages/pay/pay",
    "pages/proxy-pay/proxy-pay",
    "pages/lot-space/lot-space",
    "pages/messages/messages",
    "pages/bind-phone/bind-phone"
  ],
  "permission": {
    "scope.userLocation": {
      "desc": "用于查询附近停车场余位信息"
    }
  },
  "lazyCodeLoading": "requiredComponents",
  "window": {
    "backgroundTextStyle": "light",
    "navigationBarBackgroundColor": "#F5F7FA",
    "navigationBarTitleText": "智慧停车",
    "navigationBarTextStyle": "black",
    "backgroundColor": "#F5F7FA"
  },
  "tabBar": {
    "color": "#87989f",
    "selectedColor": "#1677FF",
    "backgroundColor": "#ffffff",
    "borderStyle": "black",
    "list": [
      {
        "pagePath": "pages/index/index",
        "text": "首页",
        "iconPath": "images/tab-home.png",
        "selectedIconPath": "images/tab-home-active.png"
      },
      {
        "pagePath": "pages/profile/profile",
        "text": "我的",
        "iconPath": "images/tab-profile.png",
        "selectedIconPath": "images/tab-profile-active.png"
      }
    ]
  },
  "style": "v2",
  "sitemapLocation": "sitemap.json"
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/app.json && git commit -m "[MINI-001] feat: register bind-phone page and permission in app.json"
```

---

### Task 28: Rewrite `app.js` — full login flow with phoneBound tracking

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/app.js`

- [ ] **Step 1: Replace the entire app.js**

```javascript
/**
 * 智慧停车 - 车主端微信小程序
 * 全局入口
 * R01 冻结契约：jushan_access_token、code===200、message 字段、displayName
 */
const TOKEN_KEY = 'jushan_access_token'
const OWNER_INFO_KEY = 'ownerInfo'

/**
 * Promise 化 wx.login
 */
function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: resolve,
      fail: reject,
    })
  })
}

App({
  globalData: {
    // 后端 API 基础地址（开发环境）
    apiBaseUrl: 'http://192.168.20.106:8080',
    // 用户登录凭证
    token: null,
    ownerInfo: null,
    // 手机号绑定状态
    phoneBound: false,
    // token 刷新锁（防止并发刷新）
    _refreshPromise: null,
    // 设备信息
    statusBarHeight: 0,
    navBarHeight: 44,
  },

  onLaunch() {
    // 获取系统信息
    const sysInfo = wx.getSystemInfoSync()
    this.globalData.statusBarHeight = sysInfo.statusBarHeight
    const menuButton = wx.getMenuButtonBoundingClientRect()
    this.globalData.navBarHeight = (menuButton.top - sysInfo.statusBarHeight) * 2 + menuButton.height

    // 启动时尝试恢复登录态
    this.restoreSession()
  },

  /**
   * 恢复会话：有 token 则验证，无 token 则主动登录
   */
  async restoreSession() {
    const token = wx.getStorageSync(TOKEN_KEY)
    if (!token) {
      await this.doLogin()
      return
    }
    this.globalData.token = token

    // 验证 token 有效性（请求 /api/v1/mini/user）
    try {
      const userInfo = await this.request({
        url: '/wx/user',
        method: 'GET',
      })
      this.globalData.ownerInfo = userInfo
      this.globalData.phoneBound = userInfo && userInfo.phoneVerified === true
    } catch (err) {
      if (err && err.status === 401) {
        this.logout()
        await this.doLogin()
      }
    }
  },

  /**
   * 主动登录：wx.login → POST /api/v1/mini/login → 存 token
   */
  async doLogin() {
    try {
      const { code } = await wxLogin()
      const result = await this.request({
        url: '/api/v1/mini/login',
        method: 'POST',
        data: { code },
        skipAuth: true,
      })
      this.globalData.token = result.token
      this.globalData.ownerInfo = result
      this.globalData.phoneBound = result.phoneBound === true
      wx.setStorageSync(TOKEN_KEY, result.token)

      // 未绑定手机号 → 引导绑定
      if (!result.phoneBound) {
        const pages = getCurrentPages()
        const currentPage = pages[pages.length - 1]
        if (currentPage && currentPage.route !== 'pages/bind-phone/bind-phone') {
          wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
        }
      }
    } catch (err) {
      wx.showModal({
        title: '登录失败',
        content: (err && err.message) || '请检查网络连接后重试',
        showCancel: false,
        confirmText: '重试',
        success: () => {
          this.doLogin()
        },
      })
    }
  },

  /**
   * 原始请求方法（绕过 request.js 封装，用于登录/刷新场景）
   */
  request(options) {
    return new Promise((resolve, reject) => {
      const header = {
        'Content-Type': 'application/json',
      }
      if (!options.skipAuth && this.globalData.token) {
        header['Authorization'] = 'Bearer ' + this.globalData.token
      }
      wx.request({
        url: this.globalData.apiBaseUrl + options.url,
        method: options.method || 'GET',
        data: options.data || {},
        header,
        success(res) {
          const body = typeof res.data === 'object' ? res.data : null
          if (res.statusCode >= 200 && res.statusCode < 300) {
            if (body && (body.code === 0 || body.code === 200)) {
              resolve(body.data)
            } else if (body && body.code !== undefined) {
              reject({ message: body.message, code: body.code, status: res.statusCode })
            } else {
              resolve(null)
            }
          } else if (res.statusCode === 401) {
            reject({ message: (body && body.message) || '未登录', status: 401 })
          } else {
            reject({ message: (body && body.message) || '请求失败', status: res.statusCode })
          }
        },
        fail(err) {
          reject({ message: err.errMsg || '网络连接失败', status: 0 })
        },
      })
    })
  },

  /**
   * 清除登录态
   */
  logout() {
    this.globalData.token = null
    this.globalData.ownerInfo = null
    this.globalData.phoneBound = false
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(OWNER_INFO_KEY)
  },
})
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/app.js && git commit -m "[MINI-001] feat: rewrite app.js with doLogin, restoreSession, phoneBound"
```

---

### Task 29: Update `request.js` — 401 auto token refresh

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/utils/request.js`

- [ ] **Step 1: Add refreshToken logic and update 401 handler**

Replace the 401 block in the `request` function. Find line 84-94 (the `if (statusCode === 401)` block). Replace from `if (statusCode === 401) {` through the closing `}` of that block:

```javascript
        if (statusCode === 401) {
          // _noRefresh prevents recursion (used by refreshToken itself)
          // _retrying prevents double-retry after a failed refresh
          if (options._noRefresh || options._retrying) {
            app.logout()
            const err = createApiError(
              (body && body.message) || '未登录或登录已过期',
              body && body.code,
              statusCode,
              body && body.traceId,
            )
            reject(err)
            return
          }
          // 尝试无感刷新 token
          refreshToken().then(newToken => {
            if (newToken) {
              options._retrying = true
              request(options).then(resolve).catch(reject)
            } else {
              app.logout()
              const err = createApiError(
                (body && body.message) || '未登录或登录已过期',
                body && body.code,
                statusCode,
                body && body.traceId,
              )
              reject(err)
            }
          }).catch(() => {
            app.logout()
            const err = createApiError(
              (body && body.message) || '未登录或登录已过期',
              body && body.code,
              statusCode,
              body && body.traceId,
            )
            reject(err)
          })
          return
        }
```

- [ ] **Step 2: Add refreshToken helper after the handleErrorResponse function**

Add before the `function get(url, params = {})` line. Insert after line 135 (`}` closing `handleErrorResponse`):

```javascript
/**
 * 无感刷新 token。
 * 使用并发锁防止多次刷新。
 */
function refreshToken() {
  const app = getApp()
  if (app.globalData._refreshPromise) {
    return app.globalData._refreshPromise
  }
  app.globalData._refreshPromise = (async () => {
    try {
      const { code } = await new Promise((resolve, reject) => {
        wx.login({ success: resolve, fail: reject })
      })
      const result = await request({
        url: '/api/v1/mini/login',
        method: 'POST',
        data: { code },
        skipAuth: true,
        _noRefresh: true,
      })
      app.globalData.token = result.token
      app.globalData.phoneBound = result.phoneBound === true
      wx.setStorageSync('jushan_access_token', result.token)
      return result.token
    } catch (err) {
      return null
    } finally {
      app.globalData._refreshPromise = null
    }
  })()
  return app.globalData._refreshPromise
}
```

- [ ] **Step 3: Add `skipAuth` support to the request function**

In the `request` function, update the header injection to support `skipAuth`:

Replace lines 47-54 (the header setup):

```javascript
    const header = {
      'Content-Type': 'application/json',
      ...(options.header || {}),
    }
    if (token && !options.skipAuth) {
      header['Authorization'] = `Bearer ${token}`
    }
```

- [ ] **Step 4: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/utils/request.js && git commit -m "[MINI-001] feat: add 401 auto token refresh in request.js"
```

---

## Phase 9: Frontend — Page Interception

### Task 30: Add phoneBound check to `pay.js`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/pay/pay.js`

- [ ] **Step 1: Add checkPhoneBinding helper and onLoad guard**

Add the following helper function before the `Page({` call:

```javascript
/**
 * 检查手机号绑定状态，未绑定时弹窗引导。
 * @returns {boolean} 已绑定返回 true
 */
function checkPhoneBinding() {
  const app = getApp()
  if (!app.globalData.token) {
    wx.showModal({
      title: '请先登录',
      content: '需要登录后才能使用此功能',
      confirmText: '去登录',
      success: (res) => {
        if (res.confirm) {
          app.doLogin()
        } else {
          wx.switchTab({ url: '/pages/index/index' })
        }
      },
    })
    return false
  }
  if (!app.globalData.phoneBound) {
    wx.showModal({
      title: '请先绑定手机号',
      content: '绑定手机号后即可使用停车缴费等服务',
      confirmText: '去绑定',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
        }
      },
    })
    return false
  }
  return true
}
```

- [ ] **Step 2: Add check to onLoad**

In the `onLoad(options)` function, add the guard at the beginning:

```javascript
  onLoad(options) {
    if (!checkPhoneBinding()) {
      return
    }
    // ... rest of existing onLoad code
```

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/pay/pay.js && git commit -m "[MINI-001] feat: add phoneBound guard to pay page"
```

---

### Task 31: Add phoneBound check to `proxy-pay.js`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/proxy-pay/proxy-pay.js`

- [ ] **Step 1: Add checkPhoneBinding helper before Page({**

```javascript
function checkPhoneBinding() {
  var app = getApp()
  if (!app.globalData.token) {
    wx.showModal({
      title: '请先登录',
      content: '需要登录后才能使用此功能',
      confirmText: '去登录',
      success: function (res) {
        if (res.confirm) {
          app.doLogin()
        } else {
          wx.switchTab({ url: '/pages/index/index' })
        }
      },
    })
    return false
  }
  if (!app.globalData.phoneBound) {
    wx.showModal({
      title: '请先绑定手机号',
      content: '绑定手机号后即可使用代缴停车费等服务',
      confirmText: '去绑定',
      success: function (res) {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
        }
      },
    })
    return false
  }
  return true
}
```

- [ ] **Step 2: Add guard at onLoad start**

```javascript
  onLoad() {
    if (!checkPhoneBinding()) {
      return
    }
    this.getClipboardPlate()
  },
```

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/proxy-pay/proxy-pay.js && git commit -m "[MINI-001] feat: add phoneBound guard to proxy-pay page"
```

---

### Task 32: Add phoneBound check to `plate.js`

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/plate/plate.js`

- [ ] **Step 1: Add checkPhoneBinding before Page({**

```javascript
function checkPhoneBinding() {
  var app = getApp()
  if (!app.globalData.token) {
    wx.showModal({
      title: '请先登录',
      content: '需要登录后才能使用此功能',
      confirmText: '去登录',
      success: function (res) {
        if (res.confirm) {
          app.doLogin()
        } else {
          wx.switchTab({ url: '/pages/index/index' })
        }
      },
    })
    return false
  }
  if (!app.globalData.phoneBound) {
    wx.showModal({
      title: '请先绑定手机号',
      content: '绑定手机号后即可管理您的车辆',
      confirmText: '去绑定',
      success: function (res) {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
        }
      },
    })
    return false
  }
  return true
}
```

- [ ] **Step 2: Add guard at onLoad start**

Replace the onLoad method:

```javascript
  onLoad() {
    if (!checkPhoneBinding()) {
      return
    }
    this.loadPlates()
  },
```

- [ ] **Step 3: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/plate/plate.js && git commit -m "[MINI-001] feat: add phoneBound guard to plate page"
```

---

### Task 33: Update `profile.js` to reflect new login state

**Files:**
- Modify: `/Users/zengbohan/Documents/project/jushan-platform/miniapp/pages/profile/profile.js`

- [ ] **Step 1: Replace the entire profile.js**

```javascript
/**
 * 我的 — 个人中心
 * T10 占位页面
 */
Page({
  data: {
    isLoggedIn: false,
    displayName: '',
    phone: '',
    phoneBound: false,
  },

  onLoad() {
    this.refreshState()
  },

  onShow() {
    this.refreshState()
  },

  /** 刷新登录状态 */
  refreshState() {
    var app = getApp()
    var token = app.globalData.token
    var ownerInfo = app.globalData.ownerInfo
    if (token && ownerInfo) {
      this.setData({
        isLoggedIn: true,
        displayName: ownerInfo.displayName || ownerInfo.nickname || '车主',
        phone: ownerInfo.phone || ownerInfo.maskedPhone || '',
        phoneBound: app.globalData.phoneBound || false,
      })
    } else {
      this.setData({
        isLoggedIn: false,
        displayName: '',
        phone: '',
        phoneBound: false,
      })
    }
  },

  /** 退出登录 */
  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: function (res) {
        if (res.confirm) {
          var app = getApp()
          app.logout()
          this.setData({ isLoggedIn: false, displayName: '', phone: '', phoneBound: false })
          wx.showToast({ title: '已退出', icon: 'success' })
        }
      }.bind(this),
    })
  },

  /** 跳转消息中心 */
  goToMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' })
  },

  /** 跳转我的车辆 */
  goToVehicles() {
    wx.showToast({ title: '我的车辆 — 后续版本开放', icon: 'none' })
  },

  /** 跳转我的月卡 */
  goToMonthCards() {
    wx.showToast({ title: '我的月卡 — 后续版本开放', icon: 'none' })
  },

  /** 跳转优惠券 */
  goToCoupons() {
    wx.showToast({ title: '优惠券 — 后续版本开放', icon: 'none' })
  },
})
```

- [ ] **Step 2: Commit**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git add miniapp/pages/profile/profile.js && git commit -m "[MINI-001] feat: update profile.js to reflect phoneBound login state"
```

---

## Phase 10: Final Verification

### Task 34: Verify backend builds and tests pass

- [ ] **Step 1: Full clean build**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn clean compile -pl parking-boot -am 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Run tests**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && mvn test -pl parking-boot -am 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`

### Task 35: Verify all miniapp files exist

- [ ] **Step 1: Check all expected miniapp files**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && echo "=== Checking miniapp files ===" && for f in \
  miniapp/app.js \
  miniapp/app.json \
  miniapp/utils/request.js \
  miniapp/pages/bind-phone/bind-phone.js \
  miniapp/pages/bind-phone/bind-phone.json \
  miniapp/pages/bind-phone/bind-phone.wxml \
  miniapp/pages/bind-phone/bind-phone.wxss \
  miniapp/pages/lot-space/lot-space.json \
  miniapp/pages/messages/messages.json \
  miniapp/pages/proxy-pay/proxy-pay.json \
  miniapp/pages/pay/pay.js \
  miniapp/pages/proxy-pay/proxy-pay.js \
  miniapp/pages/plate/plate.js \
  miniapp/pages/profile/profile.js; do \
  if [ -f "$f" ]; then echo "  OK: $f"; else echo "  MISSING: $f"; fi; done
```

Expected: All files show `OK`.

### Task 36: Verify nothing is broken — git status and final review

- [ ] **Step 1: Check git status for unexpected changes**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git status --short
```

- [ ] **Step 2: Review the diff summary**

```bash
cd /Users/zengbohan/Documents/project/jushan-platform && git diff --stat HEAD
```

---

## Implementation Summary

**Total tasks:** 37
**Backend files created:** 9
**Backend files modified:** 8
**Frontend files created:** 7
**Frontend files modified:** 8

**Key architecture decisions:**
- WeChatApiClient creates its own internal RestTemplate (no bean conflict with deviceAccessRestTemplate)
- JWT audience field `aud=miniapp` distinguishes mini-program tokens from web/admin tokens
- Prod profile has `@PostConstruct` validation that throws on `wx.mock-login=true`
- Old `/wx/login` delegates to MiniAuthService; `/wx/phone` returns HTTP 410 Gone
- Frontend token refresh uses a `_refreshPromise` lock to prevent concurrent refreshes
- phoneBound guard uses `wx.showModal` for user-friendly interception, not silent blocking
