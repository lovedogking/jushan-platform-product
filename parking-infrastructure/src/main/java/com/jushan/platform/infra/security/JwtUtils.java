package com.jushan.platform.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类。
 * <p>
 * 提供 Token 生成、验证、解析功能。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class JwtUtils {

    private JwtUtils() {}

    /**
     * 生成 JWT Token。
     *
     * @param userId     用户ID
     * @param tenantId   租户ID（平台用户为 null）
     * @param userType   用户类型（platform/tenant）
     * @param roles      角色JSON字符串
     * @param permissions 权限编码列表（逗号分隔）
     * @param secretKey  密钥
     * @param expiration 过期时间（毫秒）
     * @return JWT Token
     */
    public static String generateToken(Long userId, Long tenantId, String userType, String roles,
                                       String permissions, String secretKey, long expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("userType", userType)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

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

    /**
     * 验证 Token 是否有效。
     *
     * @param token     JWT Token
     * @param secretKey 密钥
     * @return 是否有效
     */
    public static boolean validateToken(String token, String secretKey) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 Token，提取 Claims。
     *
     * @param token     JWT Token
     * @param secretKey 密钥
     * @return Claims
     */
    public static Claims parseClaims(String token, String secretKey) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Claims 中提取用户ID。
     *
     * @param claims Claims
     * @return 用户ID
     */
    public static Long getUserIdFromClaims(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Claims 中提取租户ID。
     *
     * @param claims Claims
     * @return 租户ID（可能为 null）
     */
    public static Long getTenantIdFromClaims(Claims claims) {
        Object tenantId = claims.get("tenantId");
        return tenantId != null ? Long.parseLong(tenantId.toString()) : null;
    }

    /**
     * 从 Claims 中提取用户类型。
     *
     * @param claims Claims
     * @return 用户类型
     */
    public static String getUserTypeFromClaims(Claims claims) {
        return claims.get("userType", String.class);
    }

    /**
     * 从 Claims 中提取角色JSON。
     *
     * @param claims Claims
     * @return 角色JSON字符串
     */
    public static String getRolesFromClaims(Claims claims) {
        return claims.get("roles", String.class);
    }

    /**
     * 从 Claims 中提取权限编码列表（逗号分隔）。
     *
     * @param claims Claims
     * @return 权限编码列表
     */
    public static String getPermissionsFromClaims(Claims claims) {
        return claims.get("permissions", String.class);
    }

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
}
