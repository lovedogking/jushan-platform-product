# Webhook 签名规范

> 版本：v1.0 | 更新日期：2026-07-18 | 适用范围：Jushan 停车 SaaS 平台设备适配器对接

---

## 1. 概述

平台对外暴露的 Webhook 接入点（`POST /api/v1/device-webhook/*`）**强制要求 HMAC-SHA256 签名校验**。所有适配器向平台上报识别事件、设备状态时，必须携带合法的签名请求头。校验失败统一返回 `HTTP 401 Unauthorized`，不暴露具体失败原因。

签名校验实现类：`com.jushan.platform.modules.device.webhook.WebhookVerificationFilter`

---

## 2. 签名算法

```
sign = Base64(HMAC-SHA256(secret, timestamp + nonce + rawBody))
```

**参数说明：**

| 参数 | 说明 |
|------|------|
| `secret` | 停车场级的 Webhook Secret，由平台管理员在 `webhook_secret` 表中配置后分发给适配器部署方 |
| `timestamp` | Unix 毫秒时间戳字符串，对应请求头 `X-Timestamp` |
| `nonce` | 随机一次性字符串（建议 UUID v4），对应请求头 `X-Nonce` |
| `rawBody` | HTTP 请求原始 Body 字节（UTF-8），**不做任何预处理** |

> **注意：** 拼接顺序为 `timestamp + nonce + rawBody`（字符串拼接，无分隔符），再计算 HMAC。

---

## 3. 请求头规范

每个 Webhook 请求必须携带以下三个请求头：

| 请求头 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `X-Sign` | `string` | Base64 编码的签名字符串 | `dGhpcyBpcyBhbiBleGFtcGxl...` |
| `X-Timestamp` | `string` | Unix 毫秒时间戳（请求发起时） | `1752816000000` |
| `X-Nonce` | `string` | 随机串（建议 UUID v4），用于防重放 | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |

### 请求示例

```http
POST /api/v1/device-webhook/recognition HTTP/1.1
Host: your-platform.example.com
Content-Type: application/json; charset=UTF-8
X-Sign: J9nG7pQmLxR4tY8vK2wB5dF...
X-Timestamp: 1752816000000
X-Nonce: a1b2c3d4-e5f6-7890-abcd-ef1234567890

{"deviceSn":"DEV-2024-001","eventId":"evt-abcdef01","plateNumber":"京A12345","direction":"ENTRY","...":"..."}
```

---

## 4. 校验规则

平台在接收请求后依次执行以下校验（任一失败返回 401）：

### 4.1 请求头存在性

- `X-Sign`、`X-Timestamp`、`X-Nonce` 三者**任一缺失**或为空 → 401

### 4.2 时间戳偏差

- `X-Timestamp` 必须为合法的 Unix 毫秒时间戳
- 与服务器当前时间的偏差不得超过 **±5 分钟**（`TIMESTAMP_TOLERANCE = Duration.ofMinutes(5)`）
- 适配器端必须保证系统时钟与 NTP 同步

### 4.3 Nonce 防重放

- 平台使用 Redis `SETNX` 记录已使用的 nonce，TTL 为 **10 分钟**（`NONCE_TTL = Duration.ofMinutes(10)`）
- 同一 nonce 在 10 分钟内重复出现 → 401
- **适配器端每次请求必须生成新的 nonce**（推荐使用 UUID v4）

### 4.4 deviceSn 提取

- 请求体 JSON 中必须包含 `deviceSn` 字段
- 平台通过 `deviceSn` 查找对应设备记录（`device` 表），进而获取所属停车场的 secret
- `deviceSn` 不存在于请求体或设备记录未找到 → 401

### 4.5 签名比对

- 平台使用停车场级 secret 按上述算法计算签名，与 `X-Sign` 头值**严格匹配**（区分大小写）
- 不匹配 → 401

---

## 5. Secret 管理

- **粒度**：每个停车场（`parking_lot`）独立配置一个 `webhook_secret`
- **配置方式**：平台管理员在运营端系统参数中为停车场配置 secret，适配器部署方从管理员处获取
- **存储**：secret 存储于数据库 `webhook_secret` 表（`secret` 字段），平台在校验时按 `parking_lot_id` 查询
- **安全建议**：
  - Secret 长度建议 ≥ 32 字符，包含大小写字母、数字、特殊字符
  - 通过安全通道（如加密 IM、离线交付）分发给适配器部署方
  - 定期轮换 secret（轮换后通知适配器同步更新）

---

## 6. 错误响应

签名校验失败时，平台统一返回以下响应（不区分具体失败原因，防探测）：

```json
HTTP/1.1 401 Unauthorized
Content-Type: application/json; charset=UTF-8

{
  "code": 401,
  "message": "Unauthorized"
}
```

---

## 7. 签名开关

签名校验功能可通过配置项控制：

```yaml
device-access:
  webhook:
    signature-enabled: true   # false 时跳过签名校验（仅限开发/测试环境）
```

生产环境必须设为 `true`。

---

## 8. 示例代码（伪代码）

```java
// 适配器端签名构造伪代码（Java 风格）
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.UUID;

public class WebhookSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * 构造签名请求头。
     *
     * @param secret   停车场 Webhook Secret
     * @param rawBody  请求体 JSON 字符串
     * @return 包含 X-Sign、X-Timestamp、X-Nonce 的 Map
     */
    public static Map<String, String> sign(String secret, String rawBody) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();

        // 拼接 payload：timestamp + nonce + rawBody（无分隔符，注意顺序）
        String payload = timestamp + nonce + rawBody;

        // HMAC-SHA256
        String sign = computeHmacSha256(secret, payload);

        return Map.of(
            "X-Sign", sign,
            "X-Timestamp", String.valueOf(timestamp),
            "X-Nonce", nonce
        );
    }

    private static String computeHmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 计算失败", e);
        }
    }
}

// 使用示例：
// Map<String, String> headers = WebhookSigner.sign(
//     "your-parking-lot-secret-here",
//     "{\"deviceSn\":\"DEV-2024-001\",\"plateNumber\":\"京A12345\",\"direction\":\"ENTRY\"}"
// );
// httpClient.post(url, headers, rawBody);
```

---

## 9. 参考

- 平台源码实现：`parking-system/.../webhook/WebhookVerificationFilter.java`
- 安全管理配置：`parking-system/.../webhook/WebhookSecurityConfig.java`
- Redis Nonce 存储格式：`webhook:nonce:{nonce}`，值为 `"1"`，TTL 10 分钟
