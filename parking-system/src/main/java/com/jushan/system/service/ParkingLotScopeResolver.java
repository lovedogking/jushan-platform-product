package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.entity.EmployeeParkingLot;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.mapper.EmployeeParkingLotMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 停车场数据范围解析器（集中式 P0 安全组件）。
 * <p>
 * 根据当前登录用户的角色和授权记录，解析其可访问的停车场 ID 集合。
 * 本组件是停车场级数据隔离的<b>唯一权威来源</b>——所有业务模块必须通过本组件
 * 获取停车场范围，禁止各模块直接查询 {@code employee_parking_lot} 表。
 * <p>
 * <strong>数据范围规则</strong>：
 * <ul>
 *   <li>平台用户（super_admin / platform_operator）：全平台所有停车场</li>
 *   <li>客户管理员（customer_admin）：本租户全部停车场</li>
 *   <li>受限角色（parking_manager / device_maintenance / finance / booth_operator）：
 *       仅 {@code employee_parking_lot} 表中明确授权的停车场</li>
 * </ul>
 * <p>
 * <strong>返回值语义</strong>：
 * <ul>
 *   <li>{@code null} — 无范围限制（全量访问），调用方不应添加停车场过滤条件</li>
 *   <li>空 Set — 无授权停车场，调用方应返回空结果或拒绝访问</li>
 *   <li>非空 Set — 仅允许访问集合内的停车场</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Component
public class ParkingLotScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(ParkingLotScopeResolver.class);

    /** 拥有本租户全部停车场访问权的角色 */
    private static final Set<String> FULL_TENANT_ACCESS_ROLES = Set.of("customer_admin");

    private final EmployeeParkingLotMapper employeeParkingLotMapper;
    private final ParkingLotMapper parkingLotMapper;

    public ParkingLotScopeResolver(EmployeeParkingLotMapper employeeParkingLotMapper,
                                   ParkingLotMapper parkingLotMapper) {
        this.employeeParkingLotMapper = employeeParkingLotMapper;
        this.parkingLotMapper = parkingLotMapper;
    }

    // ==================== 公共方法 ====================

    /**
     * 解析当前用户可访问的停车场 ID 集合。
     * <p>
     * <strong>返回值语义</strong>：
     * <ul>
     *   <li>{@code null} — 无范围限制（平台用户 / 客户管理员等全量角色），
     *       调用方不应添加停车场 IN 过滤条件</li>
     *   <li>空 {@code Set} — 当前用户没有被授权任何停车场，
     *       调用方应返回空列表或拒绝访问（fail-closed）</li>
     *   <li>非空 {@code Set} — 仅允许访问这些停车场</li>
     * </ul>
     *
     * @return 授权的停车场 ID 集合，或 null 表示全量访问
     * @throws BusinessException 上下文未初始化时抛出 UNAUTHORIZED
     */
    public Set<Long> resolveAuthorizedIds() {
        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或会话已过期");
        }

        // 1. 平台用户：全量访问
        if (ctx.isPlatformUser()) {
            log.debug("平台用户（{}），返回全量范围", ctx.userType());
            return null;
        }

        // 2. 租户用户
        Long tenantId = ctx.tenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED,
                    "租户用户缺少租户绑定信息，请重新登录");
        }

        List<String> roles = parseRoles(ctx.roles());

        // 3. 客户管理员：本租户全部停车场
        if (hasAnyRole(roles, FULL_TENANT_ACCESS_ROLES)) {
            log.debug("全量租户角色（{}），返回本租户全部停车场", roles);
            return null;
        }

        // 4. 受限角色：从 employee_parking_lot 查询授权
        Long userId = ctx.userId();
        if (userId == null) {
            log.warn("受限角色但 userId 为空，返回空集合: roles={}", roles);
            return Collections.emptySet();
        }

        List<EmployeeParkingLot> auths = employeeParkingLotMapper.selectList(
                new LambdaQueryWrapper<EmployeeParkingLot>()
                        .eq(EmployeeParkingLot::getEmployeeId, userId));

        Set<Long> authorizedIds = auths.stream()
                .map(EmployeeParkingLot::getParkingLotId)
                .collect(Collectors.toSet());

        // 额外验证：授权的停车场必须属于本租户（防御性检查）
        if (!authorizedIds.isEmpty()) {
            List<ParkingLot> validLots = parkingLotMapper.selectList(
                    new LambdaQueryWrapper<ParkingLot>()
                            .in(ParkingLot::getId, authorizedIds)
                            .eq(ParkingLot::getTenantId, tenantId));
            Set<Long> validIds = validLots.stream()
                    .map(ParkingLot::getId)
                    .collect(Collectors.toSet());

            if (validIds.size() < authorizedIds.size()) {
                log.warn("用户 {} 的授权中包含非本租户停车场（已过滤）: userId={}, tenantId={}",
                        userId, userId, tenantId);
            }
            return validIds;
        }

        log.debug("用户 {} 无任何停车场授权: userId={}, tenantId={}, roles={}",
                userId, userId, tenantId, roles);
        return Collections.emptySet();
    }

    /**
     * 校验当前用户是否有权访问指定停车场。
     * <p>
     * 对全量访问角色（返回 null 的情况）直接通过；对受限角色校验停车场 ID 是否在授权集合中。
     * <p>
     * <strong>使用场景</strong>：详情查询、更新、删除、状态变更等单条记录操作。
     *
     * @param parkingLotId 停车场 ID
     * @throws BusinessException 无权访问时抛出 FORBIDDEN
     */
    public void validateAccess(Long parkingLotId) {
        if (parkingLotId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场 ID 不能为空");
        }
        Set<Long> authorizedIds = resolveAuthorizedIds();
        // null = 全量访问
        if (authorizedIds == null) {
            return;
        }
        if (!authorizedIds.contains(parkingLotId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "无权访问该停车场");
        }
    }

    /**
     * 批量校验停车场访问权限。
     * <p>
     * 批量参数中混入未授权停车场时，整体拒绝（fail-closed）。
     *
     * @param parkingLotIds 待校验的停车场 ID 集合
     * @throws BusinessException 任一 ID 不在授权范围内时抛出 FORBIDDEN
     */
    public void validateAccessBatch(Collection<Long> parkingLotIds) {
        if (parkingLotIds == null || parkingLotIds.isEmpty()) {
            return;
        }
        Set<Long> authorizedIds = resolveAuthorizedIds();
        // null = 全量访问
        if (authorizedIds == null) {
            return;
        }
        for (Long lotId : parkingLotIds) {
            if (!authorizedIds.contains(lotId)) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN,
                        "无权访问停车场: " + lotId);
            }
        }
    }

    /**
     * 判断当前是否为全量访问角色（平台用户 / 客户管理员）。
     * <p>
     * 供调用方在列表查询中决定是否需要添加停车场 IN 过滤条件。
     *
     * @return true 表示无范围限制
     */
    public boolean hasFullAccess() {
        Set<Long> ids = resolveAuthorizedIds();
        return ids == null;
    }

    // ==================== 私有方法 ====================

    /**
     * 解析 roles JSON 数组字段为角色编码列表。
     */
    private List<String> parseRoles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String trimmed = rolesJson.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String inner = trimmed.substring(1, trimmed.length() - 1).trim();
                if (inner.isEmpty()) {
                    return Collections.emptyList();
                }
                return Arrays.stream(inner.replace("\"", "").split("\\s*,\\s*"))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("解析 roles 字段失败: {}", rolesJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * 判断用户角色列表中是否包含目标角色集合中的任意一个。
     */
    private boolean hasAnyRole(List<String> userRoles, Set<String> targetRoles) {
        for (String role : userRoles) {
            if (targetRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
