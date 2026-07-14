package com.jushan.platform.infra.security;

import com.jushan.common.auth.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 默认权限提供者（兜底实现）。
 * <p>
 * 当业务模块未提供 {@link PermissionProvider} 时使用。
 * <ul>
 *   <li>平台超级管理员默认持有所有权限（便于初始化与联调）。</li>
 *   <li>其他用户没有任何权限，所有受控接口均返回 403。</li>
 * </ul>
 * 生产环境应由业务模块提供基于数据库 + Redis 的实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
@ConditionalOnMissingBean(PermissionProvider.class)
public class FallbackPermissionProvider implements PermissionProvider {

    @Override
    public Set<String> getPermissions(Long userId) {
        if (TenantContext.isPlatformUser()) {
            // 平台用户初始化阶段放行所有权限
            return Collections.singleton("*");
        }
        return Collections.emptySet();
    }

    @Override
    public void refresh(Long userId) {
        // 兜底实现无需刷新
    }
}
