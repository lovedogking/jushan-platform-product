package com.jushan.framework.ws;
import org.springframework.messaging.support.ChannelInterceptor;

/**
 * WebSocket topic 访问检查器接口。
 * <p>
 * 由业务模块实现，用于在 SUBSCRIBE/SEND 阶段执行细粒度数据范围校验。
 * 框架层 {@link WsChannelAuthInterceptor} 会在认证通过后调用所有实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface WsTopicAccessChecker extends ChannelInterceptor {

    /**
     * 返回本检查器负责的目标前缀。
     * <p>
     * 仅当 destination 以此前缀开头时，框架才会调用
     * {@link #checkAccess(String, Long, Long, String)}。
     *
     * @return 目标前缀，如 {@code /topic/booth/}
     */
    String supportedDestinationPrefix();

    /**
     * 校验当前用户是否有权订阅/发送到指定 destination。
     *
     * @param destination topic 路径
     * @param loginId     登录用户 ID
     * @param tenantId    租户 ID（可能为 null）
     * @param userType    用户类型（platform / tenant）
     * @throws org.springframework.messaging.MessageDeliveryException 无权访问时抛出
     */
    void checkAccess(String destination, Long loginId, Long tenantId, String userType);
}
