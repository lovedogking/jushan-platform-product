package com.jushan.system.ws;

import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.framework.ws.WsTopicAccessChecker;
import com.jushan.system.service.ParkingLotScopeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Component;

/**
 * 岗亭监控 topic 访问检查器（P005）。
 *
 * 校验 {@code /topic/booth/{parkingLotId}/**} 订阅权限：
 *   仅允许租户用户订阅，平台用户禁止访问岗亭 topic
 *   通过 {@link ParkingLotScopeResolver} 校验停车场授权范围
 *   parkingLotId 解析失败或无权访问时 fail-closed，断开连接
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class BoothTopicAccessChecker implements WsTopicAccessChecker {

    private static final Logger log = LoggerFactory.getLogger(BoothTopicAccessChecker.class);

    /** 岗亭监控 topic 前缀。 */
    private static final String BOOTH_TOPIC_PREFIX = "/topic/booth/";

    private final ParkingLotScopeResolver scopeResolver;

    public BoothTopicAccessChecker(ParkingLotScopeResolver scopeResolver) {
        this.scopeResolver = scopeResolver;
    }

    @Override
    public String supportedDestinationPrefix() {
        return BOOTH_TOPIC_PREFIX;
    }

    @Override
    public void checkAccess(String destination, Long loginId, Long tenantId, String userType) {
        Long parkingLotId = extractParkingLotId(destination);
        if (parkingLotId == null) {
            log.warn("WebSocket 岗亭 topic 中停车场 ID 非法，拒绝订阅: destination={}", destination);
            throw new MessageDeliveryException("岗亭监控主题中停车场 ID 非法");
        }

        TenantContext.Snapshot previous = TenantContext.get();
        try {
            TenantContext.Snapshot snapshot = buildSnapshot(loginId, tenantId, userType);
            log.debug("WebSocket 岗亭 topic 上下文: loginId={}, tenantId={}, userType={}",
                    loginId, tenantId, userType);
            if (snapshot == null) {
                log.warn("WebSocket 无法构建租户上下文，拒绝订阅岗亭 topic: destination={}", destination);
                throw new MessageDeliveryException("无法获取用户会话上下文");
            }

            // 岗亭接口仅限租户用户
            if (snapshot.isPlatformUser()) {
                log.warn("WebSocket 平台用户尝试订阅岗亭 topic，拒绝: destination={}, loginId={}",
                        destination, loginId);
                throw new MessageDeliveryException("平台用户无权访问岗亭监控");
            }

            TenantContext.set(snapshot);
            scopeResolver.validateAccess(parkingLotId);

            log.debug("WebSocket 岗亭 topic 订阅校验通过: destination={}, loginId={}, parkingLotId={}",
                    destination, loginId, parkingLotId);
        } catch (BusinessException e) {
            log.warn("WebSocket 岗亭 topic 订阅被拒绝: destination={}, parkingLotId={}, reason={}",
                    destination, parkingLotId, e.getMessage());
            throw new MessageDeliveryException("无权访问该停车场监控主题");
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    /**
     * 从 topic 路径中提取停车场 ID。
     */
    private Long extractParkingLotId(String destination) {
        if (destination == null || !destination.startsWith(BOOTH_TOPIC_PREFIX)) {
            return null;
        }
        String remainder = destination.substring(BOOTH_TOPIC_PREFIX.length());
        int slashIndex = remainder.indexOf('/');
        String lotIdPart = slashIndex > 0 ? remainder.substring(0, slashIndex) : remainder;
        try {
            return Long.valueOf(lotIdPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 根据 WebSocket 上下文构建租户上下文快照。
     *
     * @param loginId  登录用户 ID
     * @param tenantId 握手阶段获取的租户 ID（可能为 null）
     * @param userType 握手阶段获取的用户类型（可能为 null）
     * @return 快照，无法构建时返回 null
     */
    private TenantContext.Snapshot buildSnapshot(Long loginId, Long tenantId, String userType) {
        if (loginId == null) {
            return null;
        }
        if (userType == null) {
            userType = tenantId != null
                    ? TenantContext.USER_TYPE_TENANT
                    : TenantContext.USER_TYPE_PLATFORM;
        }
        return new TenantContext.Snapshot(tenantId, loginId, userType, null, null);
    }
}
