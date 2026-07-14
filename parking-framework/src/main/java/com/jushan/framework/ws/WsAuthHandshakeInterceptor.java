package com.jushan.framework.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Base64;
import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 *
 * 在 WebSocket 升级握手阶段从 JWT Token 中解析用户标识和租户上下文，
 * 认证成功后将信息写入握手属性，供后续消息拦截器使用。
 *
 * Token 提取优先级：
 *   1. URL 查询参数 {@code token}
 *   2. 握手请求头 {@code Authorization}
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsAuthHandshakeInterceptor.class);

    static final String TOKEN_PARAM = "token";
    static final String AUTH_HEADER = "Authorization";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);

        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket 握手被拒绝：未提供 token, uri={}", request.getURI());
            return false;
        }

        JwtPayload jwtPayload = resolveJwtPayload(token);
        if (jwtPayload == null || jwtPayload.userId == null) {
            log.warn("WebSocket 握手被拒绝：token 无效, uri={}", request.getURI());
            return false;
        }

        attributes.put(WsSessionContext.KEY_LOGIN_ID, jwtPayload.userId);
        attributes.put(WsSessionContext.KEY_SESSION_ID, request.getURI().getPath());
        if (jwtPayload.tenantId != null) {
            attributes.put(WsSessionContext.KEY_TENANT_ID, jwtPayload.tenantId);
        }
        if (jwtPayload.userType != null) {
            attributes.put(WsSessionContext.KEY_USER_TYPE, jwtPayload.userType);
        }
        log.debug("WebSocket 握手 JWT 认证成功: userId={} tenantId={} uri={}",
                jwtPayload.userId, jwtPayload.tenantId, request.getURI());
        return true;
    }

    /**
     * 解析 JWT payload（不校验签名，签名由前置 Spring Security 过滤器统一校验）。
     *
     * @param token JWT Token
     * @return payload 对象，解析失败返回 null
     */
    private JwtPayload resolveJwtPayload(String token) {
        if (token.chars().filter(ch -> ch == '.').count() != 2) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String payloadJson = new String(base64UrlDecode(parts[1]));
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            JwtPayload result = new JwtPayload();
            Object sub = payload.get("sub");
            result.userId = sub != null ? Long.valueOf(sub.toString()) : null;
            Object tenantId = payload.get("tenantId");
            result.tenantId = tenantId != null ? Long.valueOf(tenantId.toString()) : null;
            result.userType = payload.get("userType") != null ? payload.get("userType").toString() : null;
            return result;
        } catch (Exception e) {
            log.debug("JWT payload 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private byte[] base64UrlDecode(String input) {
        String padded = input + "=".repeat((4 - input.length() % 4) % 4);
        return Base64.getUrlDecoder().decode(padded);
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception ex) {
        // no-op
    }

    /**
     * 从握手请求中提取 token。
     */
    private String extractToken(ServerHttpRequest request) {
        // 1) URL 查询参数 ?token=xxx
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && TOKEN_PARAM.equals(kv[0]) && !kv[1].isBlank()) {
                    return kv[1];
                }
            }
        }

        // 2) 握手请求头 Authorization
        var authHeaders = request.getHeaders().get(AUTH_HEADER);
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (header.startsWith("Bearer ")) {
                return header.substring(7);
            }
            return header;
        }

        return null;
    }

    private static class JwtPayload {
        Long userId;
        Long tenantId;
        String userType;
    }
}
