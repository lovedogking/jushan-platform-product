package com.jushan.common.auth;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;

/**
 * 可信租户上下文（线程级别）。
 * <p>
 * 在每个 HTTP 请求中，由 {@link JwtAuthenticationFilter} 从 JWT Token 中
 * 推导租户 ID、用户 ID 和用户类型，存储到本 ThreadLocal 上下文中。
 * <b>前端传入的 tenantId 在任何情况下都不可信</b>——所有业务代码必须通过本上下文获取。
 * <p>
 * <strong>平台用户 vs 租户用户 vs 岗亭管理员</strong>：
 * <ul>
 *   <li>平台用户（super_admin）：tenantId == null, userType == "platform"</li>
 *   <li>岗亭管理员（booth）：tenantId == null, userType == "booth"（跨租户，仅限分配停车场）</li>
 *   <li>租户用户（customer_admin 等）：tenantId != null，所有数据操作限定在本租户范围内</li>
 * </ul>
 * <p>
 * <strong>生命周期</strong>：
 * <ul>
 *   <li>请求到达 → {@link JwtAuthenticationFilter} 填充</li>
 *   <li>请求处理完毕 → {@link JwtAuthenticationFilter} 清理（finally 块）</li>
 * </ul>
 * <p>
 * 异步任务、消息消费、定时任务等场景在调用前必须手动初始化上下文。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class TenantContext {

    private TenantContext() {}

    private static final ThreadLocal<Snapshot> CONTEXT = new ThreadLocal<>();

    // ==================== 快照 ====================

    /**
     * 上下文快照（不可变）。
     */
    public record Snapshot(
            Long tenantId,
            Long userId,
            String userType,
            String roles,
            String permissions) {

        /**
         * 是否为平台全局用户。
         * <p>
         * 必须同时满足两项条件：
         * <ol>
         *   <li>无租户绑定（{@code tenantId == null}）</li>
         *   <li>AuthService 明确写入的平台身份（{@code userType == "platform"}）</li>
         * </ol>
         */
        public boolean isPlatformUser() {
            return tenantId == null && USER_TYPE_PLATFORM.equals(userType);
        }

        /**
         * 是否为岗亭管理员（跨租户）。
         */
        public boolean isBoothUser() {
            return tenantId == null && USER_TYPE_BOOTH.equals(userType);
        }

        /**
         * 是否为普通租户用户。
         */
        public boolean isTenantUser() {
            return tenantId != null;
        }
    }

    // ==================== 读写 ====================

    /** 设置当前线程上下文。 */
    public static void set(Snapshot snapshot) {
        CONTEXT.set(snapshot);
    }

    /** 获取当前线程完整上下文，未初始化时返回 null。 */
    public static Snapshot get() {
        return CONTEXT.get();
    }

    /** 获取当前租户 ID（平台用户返回 null）。 */
    public static Long getTenantId() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.tenantId() : null;
    }

    /** 获取当前用户 ID（未登录时返回 null）。 */
    public static Long getUserId() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.userId() : null;
    }

    /** 获取当前用户类型（"platform" / "tenant"），未初始化时返回 null。 */
    public static String getUserType() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.userType() : null;
    }

    /** 获取当前用户角色 JSON，未初始化时返回 null。 */
    public static String getRoles() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.roles() : null;
    }

    /** 获取当前用户权限编码列表（逗号分隔），未初始化时返回 null。 */
    public static String getPermissions() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.permissions() : null;
    }

    // ==================== 强制推导 ====================

    /**
     * 获取当前租户 ID，若为平台用户（tenantId == null）则抛出异常。
     * <p>
     * 所有需要租户范围的业务操作必须调用此方法，确保 fail-close。
     *
     * @return 租户 ID（非 null）
     * @throws BusinessException 如果上下文未初始化或为平台用户
     */
    public static Long requireTenantId() {
        Snapshot s = CONTEXT.get();
        if (s == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或会话已过期");
        }
        if (s.tenantId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "平台用户无租户范围，请使用总后台管理");
        }
        return s.tenantId();
    }

    /**
     * 获取当前用户 ID，若上下文未初始化则抛出异常。
     *
     * @return 用户 ID（非 null）
     */
    public static Long requireUserId() {
        Snapshot s = CONTEXT.get();
        if (s == null || s.userId() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或会话已过期");
        }
        return s.userId();
    }

    /** 当前是否为平台用户。 */
    public static boolean isPlatformUser() {
        Snapshot s = CONTEXT.get();
        return s != null && s.isPlatformUser();
    }

    /** 当前是否为岗亭管理员。 */
    public static boolean isBoothUser() {
        Snapshot s = CONTEXT.get();
        return s != null && s.isBoothUser();
    }

    /** 当前是否为租户用户。 */
    public static boolean isTenantUser() {
        Snapshot s = CONTEXT.get();
        return s != null && s.isTenantUser();
    }

    // ==================== 生命周期 ====================

    /** 清除当前线程上下文（必须在请求/任务 finally 中调用）。 */
    public static void clear() {
        CONTEXT.remove();
    }

    // ==================== 常量 ====================

    /** 用户类型：平台用户。 */
    public static final String USER_TYPE_PLATFORM = "platform";

    /** 用户类型：岗亭管理员（跨租户）。 */
    public static final String USER_TYPE_BOOTH = "booth";

    /** 用户类型：租户用户。 */
    public static final String USER_TYPE_TENANT = "tenant";
}
