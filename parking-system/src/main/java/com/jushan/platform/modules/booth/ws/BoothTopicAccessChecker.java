package com.jushan.platform.modules.booth.ws;

import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.framework.ws.WsTopicAccessChecker;
import com.jushan.platform.modules.account.entity.SysCustomRole;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
import com.jushan.platform.modules.account.mapper.SysCustomRoleMapper;
import com.jushan.platform.modules.parking.service.ParkingLotScopeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 岗亭监控 topic 访问检查器（P005）。
 *
 * 校验 {@code /topic/booth/{parkingLotId}/**} 订阅权限：
 *   通过 {@link ParkingLotScopeResolver} 校验停车场授权范围
 *   （V1.5 起平台用户亦可订阅，平台用户为全量范围）
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
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final SysCustomRoleMapper customRoleMapper;

    public BoothTopicAccessChecker(ParkingLotScopeResolver scopeResolver,
                                   SysAdminAccountRoleMapper adminAccountRoleMapper,
                                   SysCustomRoleMapper customRoleMapper) {
        this.scopeResolver = scopeResolver;
        this.adminAccountRoleMapper = adminAccountRoleMapper;
        this.customRoleMapper = customRoleMapper;
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

            // V1.5：超管/租户管理员/岗亭管理员三角色均可订阅岗亭 topic，
            // 数据范围统一由 ParkingLotScopeResolver 校验
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
        // 租户用户必须带上角色编码：ParkingLotScopeResolver 依据 roles 判定
        // customer_admin 全量访问，roles 缺失会导致租户管理员被误判为无授权
        String rolesJson = null;
        if (tenantId != null) {
            rolesJson = loadRoleCodesJson(loginId);
        }
        return new TenantContext.Snapshot(tenantId, loginId, userType, rolesJson, null);
    }

    /**
     * 加载账号的角色编码并序列化为 JSON 数组字符串（与 JWT roles 声明同格式）。
     * 失败时返回 null（fail-closed，受限角色按授权表判定）。
     */
    private String loadRoleCodesJson(Long loginId) {
        try {
            List<Long> roleIds = adminAccountRoleMapper.selectRoleIdsByAdminAccountId(loginId);
            if (roleIds == null || roleIds.isEmpty()) {
                return null;
            }
            List<SysCustomRole> roles = customRoleMapper.selectBatchIds(roleIds);
            if (roles == null || roles.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (SysCustomRole role : roles) {
                if (role == null || role.getRoleCode() == null || role.getRoleCode().isEmpty()) {
                    continue;
                }
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(role.getRoleCode()).append("\"");
                first = false;
            }
            sb.append("]");
            return sb.length() > 2 ? sb.toString() : null;
        } catch (Exception e) {
            log.warn("WebSocket 加载账号角色编码失败，按无角色处理: loginId={}, error={}", loginId, e.getMessage());
            return null;
        }
    }
}
