package com.jushan.framework.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Map;

/**
 * WebSocket 通道鉴权与数据范围拦截器。
 * <p>
 * 在 WebSocket 消息通道层面拦截 STOMP 命令：
 * <ul>
 *   <li><strong>CONNECT</strong>：从握手属性回填登录上下文</li>
 *   <li><strong>SUBSCRIBE</strong>：校验订阅目标权限（数据范围 hooks）</li>
 *   <li><strong>SEND</strong>：校验发送目标权限</li>
 *   <li><strong>DISCONNECT</strong>：清理线程上下文</li>
 * </ul>
 * <p>
 * <strong>T12 之后</strong>：业务模块在 {@code SUBSCRIBE} 阶段实施细粒度数据范围检查：
 * <ul>
 *   <li>解析 {@code /topic/parking-lot/{lotId}/...} 中的停车场 ID</li>
 *   <li>对比当前用户授权停车场列表</li>
 *   <li>未授权目标拒绝订阅</li>
 * </ul>
 * <p>
 * 当前阶段仅完成框架 hook 占位和认证上下文传递，
 * 不执行细粒度数据范围校验（T12/T16 实现后接入）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class WsChannelAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsChannelAuthInterceptor.class);

    /**
     * 允许订阅/发送的目标前缀白名单（无权限限制的公共目标）。
     * 后续可根据业务扩展。
     */
    private static final String[] PUBLIC_DESTINATIONS = {
            "/topic/public",
            "/topic/health",
    };

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
        // 从握手属性回填上下文
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs != null) {
            Object loginId = sessionAttrs.get(WsSessionContext.KEY_LOGIN_ID);
            if (loginId != null) {
                WsSessionContext.setLoginId(loginId);
                WsSessionContext.setSessionId(accessor.getSessionId());
                log.debug("WebSocket CONNECT: loginId={} sessionId={}", loginId, accessor.getSessionId());
            }
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        log.debug("WebSocket SUBSCRIBE: destination={} sessionId={}", destination, accessor.getSessionId());

        if (destination == null) {
            return;
        }

        // 公共目标允许订阅
        for (String pub : PUBLIC_DESTINATIONS) {
            if (destination.startsWith(pub)) {
                return;
            }
        }

        // 租户/停车场私有目标：T12/T16 实现后在此处校验数据范围
        // 例如：
        //   Long currentTenantId = WsSessionContext.getTenantId();
        //   Long targetLotId = parseParkingLotId(destination);
        //   validateParkingLotAccess(currentTenantId, targetLotId);
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        log.debug("WebSocket SEND: destination={} sessionId={}", destination, accessor.getSessionId());
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        log.debug("WebSocket DISCONNECT: sessionId={}", accessor.getSessionId());
        WsSessionContext.clear();
    }
}
