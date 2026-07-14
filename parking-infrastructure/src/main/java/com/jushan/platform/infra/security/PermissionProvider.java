package com.jushan.platform.infra.security;

import java.util.Set;

/**
 * 权限提供者接口。
 * <p>
 * 由业务模块实现，根据用户 ID 获取其当前持有的所有权限码。
 * infrastructure 层通过本接口解耦，避免直接依赖业务表。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface PermissionProvider {

    /**
     * 获取指定用户的全部权限码。
     *
     * @param userId 用户ID
     * @return 权限码集合（不为 null）
     */
    Set<String> getPermissions(Long userId);

    /**
     * 刷新指定用户的权限缓存。
     *
     * @param userId 用户ID
     */
    void refresh(Long userId);
}
