package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.account.entity.SysAdminAccountParkingLot;
import com.jushan.platform.modules.account.mapper.SysAdminAccountParkingLotMapper;
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
    private static final Set<String> FULL_TENANT_ACCESS_ROLES = Set.of("super_admin", "customer_admin");

    private final EmployeeParkingLotMapper employeeParkingLotMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final SysAdminAccountParkingLotMapper accountParkingLotMapper;

    public ParkingLotScopeResolver(EmployeeParkingLotMapper employeeParkingLotMapper,
                                   ParkingLotMapper parkingLotMapper,
                                   SysAdminAccountParkingLotMapper accountParkingLotMapper) {
        this.employeeParkingLotMapper = employeeParkingLotMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.accountParkingLotMapper = accountParkingLotMapper;
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

        // 2. 岗亭管理员：从 sys_admin_account_parking_lot 获取跨租户授权停车场
        if (ctx.isBoothUser()) {
            Long boothUserId = ctx.userId();
            if (boothUserId == null) {
                log.warn("岗亭管理员 userId 为空，返回空集合");
                return Collections.emptySet();
            }
            List<SysAdminAccountParkingLot> auths = accountParkingLotMapper.selectList(
                    new LambdaQueryWrapper<SysAdminAccountParkingLot>()
                            .eq(SysAdminAccountParkingLot::getAdminAccountId, boothUserId));
            Set<Long> authorizedIds = auths.stream()
                    .map(SysAdminAccountParkingLot::getParkingLotId)
                    .collect(Collectors.toSet());
            log.debug("岗亭管理员（{}），授权停车场={}", boothUserId, authorizedIds);
            return authorizedIds;
        }

        // 3. 租户用户
        Long tenantId = ctx.tenantId();
        if (tenantId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED,
                    "租户用户缺少租户绑定信息，请重新登录");
        }

        List<String> roles = parseRoles(ctx.roles());

        // 3. 租户用户存在 AD-01 分配记录（sys_admin_account_parking_lot）时，按分配范围过滤
        Long userId = ctx.userId();
        if (userId != null) {
            List<SysAdminAccountParkingLot> accountAuths = accountParkingLotMapper.selectList(
                    new LambdaQueryWrapper<SysAdminAccountParkingLot>()
                            .eq(SysAdminAccountParkingLot::getAdminAccountId, userId));
            Set<Long> assignedIds = accountAuths.stream()
                    .map(SysAdminAccountParkingLot::getParkingLotId)
                    .collect(Collectors.toSet());
            if (!assignedIds.isEmpty()) {
                log.debug("租户用户按 AD-01 分配范围访问: userId={}, lots={}", userId, assignedIds);
                return filterToTenant(assignedIds, tenantId, userId);
            }
        }

        // 4. 客户管理员：本租户全部停车场
        if (hasAnyRole(roles, FULL_TENANT_ACCESS_ROLES)) {
            log.debug("全量租户角色（{}），返回本租户全部停车场", roles);
            return null;
        }

        // 5. 受限角色：从 employee_parking_lot 查询授权
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

        if (!authorizedIds.isEmpty()) {
            return filterToTenant(authorizedIds, tenantId, userId);
        }

        log.debug("用户 {} 无任何停车场授权: userId={}, tenantId={}, roles={}",
                userId, userId, tenantId, roles);
        return Collections.emptySet();
    }

    /**
     * 过滤授权集合，仅保留属于本租户的停车场（防御性检查）。
     */
    private Set<Long> filterToTenant(Set<Long> authorizedIds, Long tenantId, Long userId) {
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
     * 校验当前用户是否有权<b>修改指定车场的车场级参数</b>（任务包 1-1）。
     * <p>
     * 授权口径（严于 {@link #validateAccess(Long)}）：<b>仅超级管理员与租户管理员（本车场）</b>。
     * <ul>
     *   <li>平台用户（super_admin）：全平台放行。</li>
     *   <li>租户管理员（customer_admin）：必须且仅能修改<b>本租户</b>的车场参数——
     *       此处显式校验目标车场归属租户（{@link #validateAccess(Long)} 对全量角色直接放行，
     *       无法拦截跨租户，故不能复用）。</li>
     *   <li>其余受限角色（parking_manager / booth_operator 等）：一律拒绝，即便其对该车场有读权限。</li>
     * </ul>
     *
     * @param parkingLotId 车场 ID
     * @throws BusinessException 未登录 / 非管理员 / 跨租户访问时抛出
     */
    public void validateParamWriteAccess(Long parkingLotId) {
        if (parkingLotId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场 ID 不能为空");
        }
        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或会话已过期");
        }
        // 1. 平台超级管理员：全平台放行
        if (ctx.isPlatformUser()) {
            return;
        }
        // 2. 仅租户管理员可改；其余受限角色拒绝
        List<String> roles = parseRoles(ctx.roles());
        if (!hasAnyRole(roles, FULL_TENANT_ACCESS_ROLES)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "仅超级管理员与租户管理员可修改车场参数");
        }
        // 3. 租户管理员：显式校验目标车场归属本租户（受 TenantLineInnerInterceptor 约束，
        //    非本租户车场查询结果为空）
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问该停车场");
        }
    }

    /**
     * 校验当前用户是否有权<b>查看指定车场</b>（租户安全，供车场级参数只读接口使用）。
     * <p>
     * 与 {@link #validateAccess(Long)} 的差异：对客户管理员（全量租户角色）额外显式校验目标车场
     * 归属本租户，避免越权读取其它租户车场（{@code sys_config} 为租户豁免表，不受拦截器保护）。
     *
     * @param parkingLotId 车场 ID
     * @throws BusinessException 未登录 / 跨租户 / 无授权时抛出
     */
    public void validateReadAccess(Long parkingLotId) {
        if (parkingLotId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "停车场 ID 不能为空");
        }
        TenantContext.Snapshot ctx = TenantContext.get();
        if (ctx == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或会话已过期");
        }
        if (ctx.isPlatformUser()) {
            return;
        }
        Set<Long> authorizedIds = resolveAuthorizedIds();
        if (authorizedIds == null) {
            // 客户管理员：全量租户访问 → 显式校验车场归属本租户
            if (parkingLotMapper.selectById(parkingLotId) == null) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问该停车场");
            }
            return;
        }
        if (!authorizedIds.contains(parkingLotId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问该停车场");
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
