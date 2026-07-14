package com.jushan.common.mybatis;

/**
 * 租户拦截器跳过标记的 ThreadLocal 持有者。
 * <p>
 * 由 AOP 切面在方法执行前设置、执行后清理，
 * 供多租户拦截器感知当前是否应跳过租户条件注入。
 * <p>
 * 必须在 finally 块中调用 {@link #clear()}，防止线程复用导致上下文串扰。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class TenantIgnoreHolder {

    private TenantIgnoreHolder() {}

    private static final ThreadLocal<Boolean> IGNORED = ThreadLocal.withInitial(() -> false);

    /**
     * 设置当前线程是否跳过租户拦截。
     */
    public static void set(boolean ignored) {
        IGNORED.set(ignored);
    }

    /**
     * 当前线程是否处于跳过状态。
     */
    public static boolean isIgnored() {
        return Boolean.TRUE.equals(IGNORED.get());
    }

    /**
     * 清理当前线程状态。
     */
    public static void clear() {
        IGNORED.remove();
    }
}
