package com.jushan.platform.modules.device.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Webhook 签名生成工具（供适配器侧对接参考）。
 * <p>
 * 使用方法：
 * <ol>
 *   <li>设置 secret（与平台 webhook_secret 表中的密钥一致）</li>
 *   <li>构建 JSON 请求体（必须包含 deviceSn 字段）</li>
 *   <li>调用 {@link #generateHeaders(String, String)} 生成签名头</li>
 * </ol>
 * <p>
 * 也可通过 main 方法快速生成测试用签名：
 * <pre>
 * java WebhookSignatureTool "my-secret" '{"eventId":"evt-001","deviceSn":"SN-001"}'
 * </pre>
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public final class WebhookSignatureTool {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private WebhookSignatureTool() {
    }

    /**
     * 签名结果，包含请求头和签名后的 HTTP 头。
     */
    public record SignedHeaders(String xSign, String xTimestamp, String xNonce) {
    }

    /**
     * 使用当前时间戳生成签名头。
     *
     * @param secret  预共享密钥
     * @param requestBody 请求体 JSON 字符串
     * @return 签名头
     */
    public static SignedHeaders generateHeaders(String secret, String requestBody) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String sign = computeSignature(secret, timestamp, nonce, requestBody);
        return new SignedHeaders(sign, String.valueOf(timestamp), nonce);
    }

    /**
     * 使用指定时间戳和 nonce 生成签名头。
     *
     * @param secret      预共享密钥
     * @param timestamp   时间戳
     * @param nonce       随机数
     * @param requestBody 请求体 JSON 字符串
     * @return 签名头
     */
    public static SignedHeaders generateHeaders(String secret, long timestamp, String nonce, String requestBody) {
        String sign = computeSignature(secret, timestamp, nonce, requestBody);
        return new SignedHeaders(sign, String.valueOf(timestamp), nonce);
    }

    /**
     * 计算 HMAC-SHA256 签名。
     *
     * @param secret      预共享密钥
     * @param timestamp   时间戳
     * @param nonce       随机数
     * @param requestBody 请求体
     * @return Base64 编码的签名
     */
    public static String computeSignature(String secret, long timestamp, String nonce, String requestBody) {
        String payload = timestamp + nonce + requestBody;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC 签名计算失败", e);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("用法: java WebhookSignatureTool <secret> <requestBody> [timestamp] [nonce]");
            System.out.println();
            System.out.println("示例:");
            System.out.println("  java WebhookSignatureTool my-secret '{\"eventId\":\"evt-001\",\"deviceSn\":\"SN-001\"}'");
            System.exit(1);
        }

        String secret = args[0];
        String requestBody = args[1];

        long timestamp = args.length > 2 ? Long.parseLong(args[2]) : System.currentTimeMillis();
        String nonce = args.length > 3 ? args[3] : UUID.randomUUID().toString().replace("-", "");

        SignedHeaders headers = generateHeaders(secret, timestamp, nonce, requestBody);

        System.out.println("=== Webhook 签名头 ===");
        System.out.println("X-Sign:      " + headers.xSign());
        System.out.println("X-Timestamp: " + headers.xTimestamp());
        System.out.println("X-Nonce:     " + headers.xNonce());
        System.out.println();
        System.out.println("=== cURL 测试请求 ===");
        System.out.println("curl -X POST http://localhost:8080/api/v1/device-webhook/events \\");
        System.out.println("  -H 'Content-Type: application/json' \\");
        System.out.println("  -H 'X-Sign: " + headers.xSign() + "' \\");
        System.out.println("  -H 'X-Timestamp: " + headers.xTimestamp() + "' \\");
        System.out.println("  -H 'X-Nonce: " + headers.xNonce() + "' \\");
        System.out.println("  -d '" + requestBody + "'");
    }
}
