package com.jushan.framework.auth;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;

import java.util.Collection;
import java.util.Objects;

/**
 * 数据范围工具。
 * <p>
 * 提供租户隔离、停车场授权、角色校验等通用断言方法。
 * 所有断言遵循 <b>fail-close</b> 原则：非法范围一律抛出异常，不返回空集合或静默通过。
 * <p>
 * <strong>使用规范</strong>：
 * <ul>
 *   <li>Service 层在业务逻辑开始前调用本类方法进行数据范围校验</li>
 *   <li>严禁从 Controller 层传入 tenantId / parkingLotId 并直接信任</li>
 *   <li>平台用户（super_admin 等）使用 {@link #requirePlatformUser()} 或手动判断</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class DataScope {

    private DataScope() {}

    // ==================== 租户范围 ====================

    /**
     * 校验实体是否属于当前租户。
     * <p>
     * <strong>P0 安全修复</strong>：不再将 {@code currentTenantId == null} 视为平台用户。
     * 必须先通过 {@link TenantContext.Snapshot#isPlatformUser()} 显式确认平台身份后才允许跨租户访问。
     * 上下文未初始化时拒绝请求（fail-close），防止空上下文退化为平台范围。
     * <p>
     * 平台用户（{@code isPlatformUser() == true}）：允许访问所有租户数据。
     * 租户用户：要求实体的 tenantId 与当前上下文完全匹配。
     *
     * @param entityTenantId 待校验实体的租户 ID
     * @param entityLabel    实体描述（用于错误消息，如 "员工"、"停车场"）
     * @throws BusinessException 上下文未初始化、租户不匹配时抛出
     */
    public static void validateTenantMatch(Long entityTenantId, String entityLabel) {
        TenantContext.Snapshot s = TenantContext.get();
        if (s == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED,
                    "未登录或会话已过期");
        }
        // 只有显式确认为平台用户后才允许跨租户访问
        if (s.isPlatformUser()) {
            return;
        }
        // 租户用户：tenantId 不可为空，且必须匹配
        if (s.tenantId() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED,
                    "租户用户缺少租户绑定信息，请重新登录");
        }
        if (!Objects.equals(s.tenantId(), entityTenantId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    (entityLabel != null ? entityLabel : "资源") + "不属于本租户");
        }
    }

    /**
     * 校验实体是否属于当前租户，并返回租户 ID。
     * 如果当前用户是平台用户（无租户绑定），同样返回拒绝。
     *
     * @param entityTenantId 待校验实体的租户 ID
     * @param entityLabel    实体描述
     * @throws BusinessException 租户不匹配时抛出 FORBIDDEN
     */
    public static void validateTenantAccess(Long entityTenantId, String entityLabel) {
        Long currentTenantId = TenantContext.requireTenantId();
        if (!Objects.equals(currentTenantId, entityTenantId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    (entityLabel != null ? entityLabel : "资源") + "不属于本租户");
        }
    }

    // ==================== 角色校验 ====================

    /**
     * 校验当前用户包含指定角色。
     * <p>
     * <strong>P0 安全修复（FIX-01-R3）</strong>：使用 JSON 数组解析进行精确匹配，
     * 防止 {@code String.contains()} 的子串匹配漏洞。
     * 例如 {@code roleCode="admin"} 不会再误匹配 {@code "super_admin"} 或 {@code "customer_admin"}。
     *
     * @param roleCode 角色编码（如 "customer_admin"）
     * @throws BusinessException 角色不匹配时抛出 FORBIDDEN
     */
    public static void requireRole(String roleCode) {
        String roles = TenantContext.getRoles();
        if (roles == null || roles.isBlank()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "需要 " + roleCode + " 角色才能执行此操作");
        }
        // 从 JSON 数组字符串中提取角色列表进行精确匹配
        java.util.List<String> roleList = parseRoleList(roles);
        if (!roleList.contains(roleCode)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "需要 " + roleCode + " 角色才能执行此操作");
        }
    }

    /**
     * 判断当前用户是否拥有指定角色（FIX-05）。
     * <p>
     * 使用 JSON 数组精确匹配，防止 {@code String.contains()} 子串匹配漏洞。
     *
     * @param roleCode 角色编码（如 "super_admin"）
     * @return true 如果用户拥有该角色
     */
    public static boolean hasRole(String roleCode) {
        String roles = TenantContext.getRoles();
        if (roles == null || roles.isBlank()) {
            return false;
        }
        java.util.List<String> roleList = parseRoleList(roles);
        return roleList.contains(roleCode);
    }

    /**
     * 解析 roles JSON 数组字段为角色编码列表。
     * <p>
     * 支持格式：{@code ["super_admin"]}、{@code ["customer_admin","parking_manager"]}
     */
    private static java.util.List<String> parseRoleList(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            String trimmed = rolesJson.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String inner = trimmed.substring(1, trimmed.length() - 1).trim();
                if (inner.isEmpty()) {
                    return java.util.Collections.emptyList();
                }
                return java.util.Arrays.stream(inner.replace("\"", "").split("\\s*,\\s*"))
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }
            return java.util.Collections.emptyList();
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 校验当前用户为客户管理员。
     */
    public static void requireCustomerAdmin() {
        requireRole("customer_admin");
    }

    /**
     * 校验当前用户为平台全局用户（非代理、非租户）。
     * <p>
     * <strong>P0 安全修复（FIX-01-R1）</strong>：
     * 必须正向确认 {@code isPlatformUser() == true}。
     * 不再通过排除法（如 {@code !isTenantUser()}）推断平台身份，
     * 代理模式和损坏身份均会被拒绝。
     * <p>
     * 平台用户（super_admin、platform_operator）无租户绑定，
     * 可访问所有租户数据。
     *
     * @throws BusinessException 不是平台全局用户时抛出 FORBIDDEN
     */
    public static void requirePlatformUser() {
        TenantContext.Snapshot s = TenantContext.get();
        if (s == null || !s.isPlatformUser()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "此操作仅限平台管理员");
        }
    }

    // ==================== 停车场范围 ====================

    /**
     * 校验停车场 ID 列表中的所有停车场都属于指定租户。
     * 任意一个停车场不存在或不属于该租户则立即失败。
     *
     * @param parkingLotIds           待校验的停车场 ID 列表
     * @param tenantId                期望的租户 ID
     * @param idToTenantIdResolver    根据停车场 ID 查询其 tenantId 的回调
     * @param idToNameResolver        根据停车场 ID 查询其名称的回调（用于错误消息）
     * @throws BusinessException 停车场不存在或不属于该租户时抛出
     */
    public static void validateParkingLotsBelongToTenant(
            Collection<Long> parkingLotIds,
            Long tenantId,
            java.util.function.Function<Long, Long> idToTenantIdResolver,
            java.util.function.Function<Long, String> idToNameResolver) {

        if (parkingLotIds == null || parkingLotIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "授权停车场不能为空");
        }

        for (Long lotId : parkingLotIds) {
            Long lotTenantId = idToTenantIdResolver.apply(lotId);
            if (lotTenantId == null) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "停车场不存在: " + lotId);
            }
            if (!Objects.equals(tenantId, lotTenantId)) {
                String name = idToNameResolver != null ? idToNameResolver.apply(lotId) : String.valueOf(lotId);
                throw new BusinessException(CommonErrorCode.FORBIDDEN,
                        "停车场 '" + name + "' 不属于本租户");
            }
        }
    }

    // ==================== 租户用户要求 ====================

    /**
     * 确保当前用户是租户用户（非平台用户），并返回租户 ID。
     * 这也会校验上下文已初始化且已登录。
     *
     * @return 租户 ID（非 null）
     * @throws BusinessException 未登录、会话过期或为平台用户时抛出
     */
    public static Long requireTenantUser() {
        return TenantContext.requireTenantId();
    }

    /**
     * 校验租户处于启用状态。
     *
     * @param status 租户状态
     * @throws BusinessException 租户不存在或已禁用
     */
    public static void validateTenantEnabled(String status) {
        if (status == null || !"ENABLED".equals(status)) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "租户不存在或已被禁用");
        }
    }
}
