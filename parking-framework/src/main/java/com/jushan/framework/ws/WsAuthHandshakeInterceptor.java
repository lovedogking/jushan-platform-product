package com.jushan.framework.ws;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 * <p>
 * 在 WebSocket 升级握手阶段验证 Sa-Token 会话：
 * <ul>
 *   <li>从握手请求中提取 token 参数或 header</li>
 *   <li>验证 token 有效性并获取登录用户</li>
 *   <li>未认证连接直接拒绝握手</li>
 *   <li>认证成功后将登录信息写入握手属性，供后续消息拦截器使用</li>
 * </ul>
 * <p>
 * <strong>Token 提取优先级</strong>：
 * <ol>
 *   <li>URL 查询参数 {@code token}</li>
 *   <li>握手请求头 {@code Authorization}</li>
 * </ol>
 * <p>
 * <strong>T12 之前</strong>：当前未实现登录，所有连接均放行（保留 hook 占位）。
 * 后续 T12 实现登录后，将{@code ALLOW_UNAUTHENTICATED} 改为 {@code false} 即可启用。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsAuthHandshakeInterceptor.class);

    /**
     * 是否允许未认证连接（T12 登录实现后改为 false）。
     * <p>
     * 当前为 true 以支持 T09 基础设施验证；
     * 后续改为 false 后，所有未认证 WebSocket 连接将被拒绝。
     */
    private static final boolean ALLOW_UNAUTHENTICATED = true;

    static final String TOKEN_PARAM = "token";
    static final String AUTH_HEADER = "Authorization";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);

        if (token == null || token.isBlank()) {
            if (ALLOW_UNAUTHENTICATED) {
                log.debug("WebSocket 握手无 token，当前阶段放行: uri={}", request.getURI());
                return true;
            }
            log.warn("WebSocket 握手被拒绝：未提供 token, uri={}", request.getURI());
            return false;
        }

        // 验证 token
        Object loginId;
        try {
            loginId = StpUtil.getLoginIdByToken(token);
        } catch (Exception e) {
            if (ALLOW_UNAUTHENTICATED) {
                log.warn("WebSocket token 验证失败，当前阶段放行: uri={} error={}",
                        request.getURI(), e.getMessage());
                return true;
            }
            log.warn("WebSocket 握手被拒绝：token 无效, uri={}", request.getURI());
            return false;
        }

        log.debug("WebSocket 握手认证成功: loginId={} uri={}", loginId, request.getURI());

        // 写入认证信息到握手属性
        attributes.put(WsSessionContext.KEY_LOGIN_ID, loginId);
        attributes.put(WsSessionContext.KEY_SESSION_ID, request.getURI().getPath());

        return true;
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
            return authHeaders.get(0);
        }

        return null;
    }
}
