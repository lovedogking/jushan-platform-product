package com.jushan.framework.auth;

/**
 * 可信租户上下文（线程级别）。
 * <p>
 * 在每个 HTTP 请求中，由 {@link TenantContextFilter} 从 Sa-Token 已验证会话中
 * 推导租户 ID、用户 ID 和用户类型，存储到本 ThreadLocal 上下文中。
 * <b>前端传入的 tenantId 在任何情况下都不可信</b>——所有业务代码必须通过本上下文获取。
 * <p>
 * <strong>平台用户 vs 租户用户</strong>：
 * <ul>
 *   <li>平台用户（super_admin、platform_operator）：tenantId == null</li>
 *   <li>租户用户（customer_admin 等）：tenantId != null，所有数据操作限定在本租户范围内</li>
 * </ul>
 * <p>
 * <strong>生命周期</strong>：
 * <ul>
 *   <li>请求到达 → {@link TenantContextFilter} 填充</li>
 *   <li>请求处理完毕 → {@link TenantContextFilter} 清理（finally 块）</li>
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
            boolean isProxy,
            Long proxyOperatorId,
            String proxyOperatorName,
            Long proxyTargetTenantId) {

        /** 默认快照（非代理模式）。 */
        public Snapshot(Long tenantId, Long userId, String userType, String roles) {
            this(tenantId, userId, userType, roles, false, null, null, null);
        }

        /**
         * 是否为平台全局用户（非代理）。
         * <p>
         * <strong>P0 安全修复（FIX-01-R1）</strong>：
         * 必须同时满足三项条件——缺一不可：
         * <ol>
         *   <li>非代理模式（{@code isProxy == false}）</li>
         *   <li>无租户绑定（{@code tenantId == null}）</li>
         *   <li>AuthService 明确写入的平台身份（{@code userType == "platform"}）</li>
         * </ol>
         * <p>
         * <strong>代理模式不再被视为平台用户</strong>：
         * 代理请求的数据范围严格限定在目标租户内，不可跨租户访问。
         */
        public boolean isPlatformUser() {
            return !isProxy && tenantId == null && USER_TYPE_PLATFORM.equals(userType);
        }

        /**
         * 是否为普通租户用户（非代理）。
         * <p>
         * 代理模式下的请求不被视为租户用户——操作人是平台管理员，
         * 但在特定目标租户范围内操作。
         */
        public boolean isTenantUser() {
            return !isProxy && tenantId != null;
        }

        /**
         * 是否为代理模式。
         * <p>
         * 代理模式下，当前数据范围严格限定在 {@code proxyTargetTenantId} 指定的目标租户。
         * 代理身份不具有平台全局数据范围。
         */
        public boolean isProxyMode() {
            return isProxy;
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

    // ==================== 强制推导 ====================

    /**
     * 获取当前租户 ID，若为平台用户（tenantId == null）则抛出异常。
     * <p>
     * 所有需要租户范围的业务操作必须调用此方法，确保 fail-close。
     *
     * @return 租户 ID（非 null）
     * @throws com.jushan.common.BusinessException 如果上下文未初始化或为平台用户
     */
    public static Long requireTenantId() {
        Snapshot s = CONTEXT.get();
        if (s == null) {
            throw new com.jushan.common.BusinessException(
                    com.jushan.common.CommonErrorCode.UNAUTHORIZED,
                    "未登录或会话已过期");
        }
        if (s.tenantId() == null) {
            throw new com.jushan.common.BusinessException(
                    com.jushan.common.CommonErrorCode.FORBIDDEN,
                    "平台用户无租户范围，请使用总后台管理");
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
            throw new com.jushan.common.BusinessException(
                    com.jushan.common.CommonErrorCode.UNAUTHORIZED,
                    "未登录或会话已过期");
        }
        return s.userId();
    }

    /** 当前是否为平台用户。 */
    public static boolean isPlatformUser() {
        Snapshot s = CONTEXT.get();
        return s != null && s.isPlatformUser();
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

    // ==================== 快照保存/恢复（异步任务等场景） ====================

    /**
     * 创建当前上下文的快照副本（可在异步线程中恢复）。
     *
     * @return 快照副本，或 null（如果上下文未初始化）
     */
    public static Snapshot capture() {
        return CONTEXT.get();
    }

    /**
     * 在当前线程中恢复快照。
     *
     * @param snapshot 之前捕获的快照
     */
    public static void restore(Snapshot snapshot) {
        CONTEXT.set(snapshot);
    }

    /** Sa-Token Session 中存储 tenantId 的键名。 */
    public static final String SESSION_KEY_TENANT_ID = "tenantId";

    /** Sa-Token Session 中存储 userType 的键名。 */
    public static final String SESSION_KEY_USER_TYPE = "userType";

    /** Sa-Token Session 中存储 roles 的键名。 */
    public static final String SESSION_KEY_ROLES = "roles";

    /** 用户类型：平台用户。 */
    public static final String USER_TYPE_PLATFORM = "platform";

    /** 用户类型：租户用户。 */
    public static final String USER_TYPE_TENANT = "tenant";

    /** Sa-Token Session 中存储 proxy target tenantId 的键名。 */
    public static final String SESSION_KEY_PROXY_TENANT_ID = "proxyTenantId";

    /** Sa-Token Session 中存储 proxy operator id 的键名。 */
    public static final String SESSION_KEY_PROXY_OPERATOR_ID = "proxyOperatorId";

    /** Sa-Token Session 中存储 proxy operator name 的键名。 */
    public static final String SESSION_KEY_PROXY_OPERATOR_NAME = "proxyOperatorName";

    // ==================== 代理模式 ====================

    /** 当前是否为代操作模式。 */
    public static boolean isProxyMode() {
        Snapshot s = CONTEXT.get();
        return s != null && s.isProxy();
    }

    /** 获取代操作人 ID（仅在代理模式下有效）。 */
    public static Long getProxyOperatorId() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.proxyOperatorId() : null;
    }

    /** 获取代操作人名称（仅在代理模式下有效）。 */
    public static String getProxyOperatorName() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.proxyOperatorName() : null;
    }

    /** 获取代操作目标租户 ID（仅在代理模式下有效）。 */
    public static Long getProxyTargetTenantId() {
        Snapshot s = CONTEXT.get();
        return s != null ? s.proxyTargetTenantId() : null;
    }
}
