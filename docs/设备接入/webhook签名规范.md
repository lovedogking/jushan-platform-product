# Device Access Webhook 签名规范

## 概述

Device Access 向平台推送 Webhook 事件时，必须携带 HMAC-SHA256 签名，平台侧验签通过后才处理事件。未携带签名或签名不符的请求将被拒绝（HTTP 401）。

## 签名算法

```
签名 = Base64( HMAC-SHA256( secret, timestamp + nonce + rawBody ) )
```

| 参数 | 说明 |
|------|------|
| `secret` | 预共享密钥，由平台管理员在 `webhook_secret` 表中按停车场配置 |
| `timestamp` | 请求发送时的 Unix 毫秒时间戳（字符串） |
| `nonce` | 随机字符串，每次请求唯一，长度建议 32 字符 |
| `rawBody` | 请求体的原始 JSON 字节（UTF-8） |

## 请求头

每个 Webhook 请求必须携带以下三个请求头：

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Sign` | 是 | Base64 编码的 HMAC-SHA256 签名 |
| `X-Timestamp` | 是 | 请求发送时的 Unix 毫秒时间戳 |
| `X-Nonce` | 是 | 随机字符串，防重放攻击 |

### 示例

```
POST /api/v1/device-webhook/events HTTP/1.1
Host: platform.example.com
Content-Type: application/json; charset=utf-8
X-Sign: 7QbqJ8fP3kLm2nR5sT9vWxYz...
X-Timestamp: 1752700800000
X-Nonce: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6

{"eventId":"evt-001","eventType":"PLATE_RECOGNIZED","deviceSn":"SN-001",...}
```

## 时间戳校验

平台校验请求时间戳与服务器当前时间的偏差，超过 **±5 分钟** 的请求将被拒绝。

适配器侧应确保系统时钟与 NTP 同步。

## Nonce 防重放

平台使用 Redis 记录已使用的 nonce，TTL 为 10 分钟。同一 nonce 重复使用将被拒绝。

适配器侧建议：
- 使用 UUID 生成 nonce
- 每次请求生成新的 nonce
- nonce 长度 ≥ 16 字符

## 签名计算示例（Java）

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WebhookSigner {
    public static String sign(String secret, long timestamp, String nonce, String body) {
        String payload = timestamp + nonce + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
```

## 签名计算示例（Python）

```python
import hmac
import hashlib
import base64

def sign(secret: str, timestamp: int, nonce: str, body: str) -> str:
    payload = f"{timestamp}{nonce}{body}"
    mac = hmac.new(
        secret.encode('utf-8'),
        payload.encode('utf-8'),
        hashlib.sha256
    )
    return base64.b64encode(mac.digest()).decode('utf-8')
```

## 密钥管理

1. 平台管理员在 `webhook_secret` 表中为每个停车场配置独立密钥
2. 密钥通过安全渠道（如 1Password、运维配置平台）分发给适配器运维人员
3. 密钥定期轮换（建议 90 天），轮换时注意新旧密钥过渡窗口

### 数据库表结构

```sql
CREATE TABLE webhook_secret (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    parking_lot_id  BIGINT NOT NULL,
    secret          VARCHAR(256) NOT NULL,
    description     VARCHAR(500) DEFAULT '',
    status          TINYINT NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_parking_lot_id (parking_lot_id)
);
```

## 错误响应

签名校验失败时，平台返回 HTTP 401，响应体：

```json
{"code":401,"message":"Unauthorized"}
```

**注意**：出于安全考虑，平台不会返回具体失败原因（如"签名不匹配"、"时间戳过期"等），防止攻击者探测。

## 兼容性说明

- 签名校验从 V1.1 开始强制执行
- 生产环境必须启用签名校验（`device-access.webhook.signature-enabled=true`）
- 本地开发环境可关闭签名校验方便调试（`device-access.webhook.signature-enabled=false`）

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0 | 2026-07-17 | 初始版本，定义 HMAC-SHA256 签名规范 |
