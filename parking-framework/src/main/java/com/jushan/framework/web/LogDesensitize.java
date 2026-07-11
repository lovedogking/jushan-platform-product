package com.jushan.framework.web;

import java.util.regex.Pattern;

/**
 * 日志脱敏工具。
 * <p>
 * 提供对手机号、身份证号、Token 等敏感信息的掩码方法。
 * 调用者在输出日志或 toString 时主动调用本工具，不采用全局替换策略以避免误伤。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * log.info("用户 {} 登录成功", LogDesensitize.maskPhone("13812345678"));
 * // 输出：用户 138****5678 登录成功
 * }</pre>
 *
 * <strong>P0 红线</strong>：禁止在日志中输出 Token、密钥、密码、身份证号、手机号明文。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class LogDesensitize {

    private LogDesensitize() {}

    // ==================== 掩码模式 ====================

    /** 手机号：保留前 3 后 4 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");

    /** 身份证号：保留前 3 后 4 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{3})\\d{11}(\\w{4})");

    /** Token：保留前 3 后 3，至少 10 位才脱敏 */
    private static final int TOKEN_MIN_LENGTH = 10;

    // ==================== 公开方法 ====================

    /**
     * 脱敏手机号。示例：{@code 13812345678 → 138****5678}。
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return PHONE_PATTERN.matcher(phone).replaceAll("$1****$2");
    }

    /**
     * 脱敏身份证号。示例：{@code 320102199001011234 → 320***********1234}。
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 15) {
            return idCard;
        }
        return ID_CARD_PATTERN.matcher(idCard).replaceAll("$1***********$2");
    }

    /**
     * 脱敏 Token。保留前 3 后 3 字符，中间用 * 替换。
     * 短于 10 位的 token 直接返回原文（不足以形成有效脱敏）。
     */
    public static String maskToken(String token) {
        if (token == null || token.length() < TOKEN_MIN_LENGTH) {
            return token;
        }
        int maskLen = token.length() - 6;
        if (maskLen <= 0) {
            return token;
        }
        return token.substring(0, 3) + "*".repeat(maskLen) + token.substring(token.length() - 3);
    }

    /**
     * 脱敏密码/密钥类字段。直接替换为固定字符串，不保留任何原文字符。
     */
    public static String maskSecret(String value) {
        if (value == null) {
            return null;
        }
        return "***";
    }

    /**
     * 通用脱敏：根据字段名猜测脱敏策略。
     * 仅用于无法明确调用特定 mask 方法的场景，优先使用 {@link #maskPhone} 等明确方法。
     */
    public static String maskByFieldName(String fieldName, String value) {
        if (value == null || fieldName == null) {
            return value;
        }
        String lower = fieldName.toLowerCase();
        if (lower.contains("phone") || lower.contains("mobile") || lower.contains("tel")) {
            return maskPhone(value);
        }
        if (lower.contains("idcard") || lower.contains("id_card") || lower.contains("identity")) {
            return maskIdCard(value);
        }
        if (lower.contains("token") || lower.contains("secret") || lower.contains("key")
                || lower.contains("password") || lower.contains("pwd") || lower.contains("sign")) {
            return maskSecret(value);
        }
        return value;
    }
}
