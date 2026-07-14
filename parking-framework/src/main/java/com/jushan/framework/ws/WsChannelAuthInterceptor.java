package com.jushan.framework.ws;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 通道鉴权与数据范围拦截器。
 * <p>
 * 在 WebSocket 消息通道层面拦截 STOMP 命令：
 * <ul>
 *   <li><strong>CONNECT</strong>：从握手属性回填登录上下文</li>
 *   <li><strong>SUBSCRIBE</strong>：基础认证校验后，调用业务层 {@link WsTopicAccessChecker} 进行数据范围检查</li>
 *   <li><strong>SEND</strong>：校验发送目标权限</li>
 *   <li><strong>DISCONNECT</strong>：清理线程上下文</li>
 * </ul>
 * <p>
 * 业务模块通过实现 {@link WsTopicAccessChecker} 接口并注册为 Spring Bean，
 * 可对特定 topic 前缀执行细粒度数据范围校验（如停车场授权范围）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class WsChannelAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsChannelAuthInterceptor.class);

    /**
     * 允许订阅/发送的目标前缀白名单（无权限限制的公共目标）。
     */
    private static final String[] PUBLIC_DESTINATIONS = {
            "/topic/public",
            "/topic/health",
    };

    private final List<WsTopicAccessChecker> accessCheckers;

    public WsChannelAuthInterceptor(List<WsTopicAccessChecker> accessCheckers) {
        this.accessCheckers = accessCheckers != null ? accessCheckers : List.of();
    }

    @PostConstruct
    public void init() {
        log.info("WsChannelAuthInterceptor 已初始化，topic 访问检查器数量: {}", accessCheckers.size());
        for (WsTopicAccessChecker checker : accessCheckers) {
            log.info("WsChannelAuthInterceptor 注册检查器: {}", checker.getClass().getName());
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            handleSend(accessor);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            handleDisconnect(accessor);
        }

        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel,
                                     boolean sent, Exception ex) {
        // 消费线程结束后清理上下文，防止线程池复用泄漏
        WsSessionContext.clear();
    }

    // ==================== 内部处理 ====================

    private void handleConnect(StompHeaderAccessor accessor) {
        // 从握手属性回填上下文（FIX-03：包含租户信息）
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs != null) {
            Object loginId = sessionAttrs.get(WsSessionContext.KEY_LOGIN_ID);
            if (loginId != null) {
                WsSessionContext.setLoginId(loginId);
                WsSessionContext.setSessionId(accessor.getSessionId());

                // 回填租户上下文（FIX-03）
                Object tenantId = sessionAttrs.get(WsSessionContext.KEY_TENANT_ID);
                if (tenantId != null) {
                    if (tenantId instanceof Long l) {
                        WsSessionContext.setTenantId(l);
                    } else if (tenantId instanceof Number n) {
                        WsSessionContext.setTenantId(n.longValue());
                    }
                }
                Object userType = sessionAttrs.get(WsSessionContext.KEY_USER_TYPE);
                if (userType != null) {
                    WsSessionContext.setUserType(userType.toString());
                }

                log.debug("WebSocket CONNECT: loginId={} tenantId={} userType={} sessionId={}",
                        loginId, WsSessionContext.getTenantId(),
                        userType, accessor.getSessionId());
            }
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        log.debug("WebSocket SUBSCRIBE: destination={} sessionId={}", destination, accessor.getSessionId());

        if (destination == null) {
            return;
        }

        // 公共目标允许订阅（无需认证）
        for (String pub : PUBLIC_DESTINATIONS) {
            if (destination.startsWith(pub)) {
                return;
            }
        }

        // FIX-03：私有目标必须认证后才允许订阅
        // WebSocket 消息可能由不同线程处理，ThreadLocal 不可靠，必须从 Session 属性读取握手阶段写入的认证信息
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        Object loginIdObj = sessionAttrs != null ? sessionAttrs.get(WsSessionContext.KEY_LOGIN_ID) : null;
        Long loginId = toLong(loginIdObj);
        if (loginId == null) {
            log.warn("WebSocket 未认证用户尝试订阅私有目标: destination={}", destination);
            throw new org.springframework.messaging.MessageDeliveryException(
                    "未认证用户不允许订阅私有主题");
        }

        // P005：调用业务层 topic 访问检查器
        log.debug("WebSocket topic 访问检查器数量: {}", accessCheckers.size());
        for (WsTopicAccessChecker checker : accessCheckers) {
            String prefix = checker.supportedDestinationPrefix();
            log.debug("WebSocket 检查器 prefix={}", prefix);
            if (prefix != null && !prefix.isBlank() && destination.startsWith(prefix)) {
                Object tenantIdObj = sessionAttrs != null ? sessionAttrs.get(WsSessionContext.KEY_TENANT_ID) : null;
                Object userTypeObj = sessionAttrs != null ? sessionAttrs.get(WsSessionContext.KEY_USER_TYPE) : null;
                Long tenantId = toLong(tenantIdObj);
                String userType = userTypeObj != null ? userTypeObj.toString() : null;
                log.debug("WebSocket 调用 topic 访问检查器: destination={}, loginId={}, tenantId={}, userType={}",
                        destination, loginId, tenantId, userType);
                checker.checkAccess(destination, loginId, tenantId, userType);
            }
        }
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        log.debug("WebSocket SEND: destination={} sessionId={}", destination, accessor.getSessionId());

        // FIX-03：禁止客户端向服务端业务主题发送消息
        // 当前阶段业务不需要客户端 SEND 到服务端，明确禁止
        if (destination != null && !isPublicDestination(destination)) {
            Object loginId = WsSessionContext.getLoginId();
            if (loginId == null) {
                log.warn("WebSocket 未认证用户尝试发送消息到: destination={}", destination);
                throw new org.springframework.messaging.MessageDeliveryException(
                        "未认证用户不允许发送消息");
            }
        }
    }

    /**
     * 判断目标是否为公共主题（无需认证即可访问）。
     */
    private boolean isPublicDestination(String destination) {
        for (String pub : PUBLIC_DESTINATIONS) {
            if (destination.startsWith(pub)) {
                return true;
            }
        }
        return false;
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        log.debug("WebSocket DISCONNECT: sessionId={}", accessor.getSessionId());
        WsSessionContext.clear();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
