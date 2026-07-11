package com.jushan.framework.ws;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 连接与消息上下文（线程级别）。
 * <p>
 * 在 WebSocket 消息处理线程中，存储当前连接的认证会话和租户绑定信息。
 * 供消息处理器在业务逻辑中获取当前操作人、租户和数据范围。
 * <p>
 * <strong>生命周期</strong>：
 * <ul>
 *   <li>连接建立时由握手拦截器设置</li>
 *   <li>消息处理时，通道拦截器刷新 token 上下文</li>
 *   <li>连接关闭时清理</li>
 * </ul>
 * <p>
 * 使用 {@link ThreadLocal} + 消息头传递，与 STOMP/WebSocket 线程模型兼容。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class WsSessionContext {

    private WsSessionContext() {}

    private static final ThreadLocal<Map<String, Object>> CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    // --- Key 常量 ---

    /** 登录 ID（Sa-Token loginId），String 或 Long 类型 */
    public static final String KEY_LOGIN_ID = "loginId";

    /** 用户类型（如 "platform"、"tenant"） */
    public static final String KEY_USER_TYPE = "userType";

    /** 租户 ID，Long 类型 */
    public static final String KEY_TENANT_ID = "tenantId";

    /** 当前 WebSocket Session ID */
    public static final String KEY_SESSION_ID = "sessionId";

    // --- 读 ---

    public static Object get(String key) {
        return CONTEXT.get().get(key);
    }

    public static Long getLoginId() {
        Object v = get(KEY_LOGIN_ID);
        if (v instanceof Long l) return l;
        if (v != null) return Long.valueOf(v.toString());
        return null;
    }

    public static Long getTenantId() {
        Object v = get(KEY_TENANT_ID);
        if (v instanceof Long l) return l;
        if (v != null) return Long.valueOf(v.toString());
        return null;
    }

    public static String getSessionId() {
        Object v = get(KEY_SESSION_ID);
        return v != null ? v.toString() : null;
    }

    // --- 写 ---

    public static void set(String key, Object value) {
        CONTEXT.get().put(key, value);
    }

    public static void setLoginId(Object loginId) {
        set(KEY_LOGIN_ID, loginId);
    }

    public static void setTenantId(Long tenantId) {
        set(KEY_TENANT_ID, tenantId);
    }

    /** 设置用户类型：platform / tenant */
    public static void setUserType(String userType) {
        set(KEY_USER_TYPE, userType);
    }

    public static void setSessionId(String sessionId) {
        set(KEY_SESSION_ID, sessionId);
    }

    // --- 清理 ---

    /** 清除当前线程的所有上下文（必须在消息处理 finally 中调用）。 */
    public static void clear() {
        CONTEXT.remove();
    }
}
