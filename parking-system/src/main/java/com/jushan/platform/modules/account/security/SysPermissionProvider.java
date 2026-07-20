package com.jushan.platform.modules.account.security;

import com.jushan.platform.infra.security.PermissionProvider;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
import com.jushan.platform.modules.account.mapper.SysRolePermissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于数据库 + Redis 的权限提供者实现。
 * <p>
 * 从 {@code sys_admin_account_role} 和 {@code sys_role_permission} 加载管理员权限，
 * 并缓存到 Redis，Key 格式为 {@code auth:permissions:{userId}}，TTL 24 小时。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class SysPermissionProvider implements PermissionProvider {

    private static final Logger log = LoggerFactory.getLogger(SysPermissionProvider.class);

    /** 权限缓存 Key 前缀 */
    private static final String PERMISSION_KEY_PREFIX = "auth:permissions:";

    /** 缓存有效期（小时） */
    private static final long CACHE_TTL_HOURS = 24L;

    private static final Set<String> TENANT_ADMIN_DEFAULT_PERMISSIONS = Set.of(
            "parking:read", "parking:write",
            "device:read", "device:manage",
            "vehicle-list:view", "vehicle-list:create", "vehicle-list:update", "vehicle-list:delete",
            "record:view",
            "dashboard:view",
            "booth:view", "booth:monitor", "booth:operate");

    private static final Set<String> BOOTH_ADMIN_DEFAULT_PERMISSIONS = Set.of(
            "record:view",
            "booth:view", "booth:monitor", "booth:operate");

    private final StringRedisTemplate redisTemplate;
    private final SysAdminAccountMapper adminAccountMapper;
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public SysPermissionProvider(StringRedisTemplate redisTemplate,
                                 SysAdminAccountMapper adminAccountMapper,
                                 SysAdminAccountRoleMapper adminAccountRoleMapper,
                                 SysRolePermissionMapper rolePermissionMapper) {
        this.redisTemplate = redisTemplate;
        this.adminAccountMapper = adminAccountMapper;
        this.adminAccountRoleMapper = adminAccountRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public Set<String> getPermissions(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }

        String key = buildKey(userId);

        // 1. 先读 Redis 缓存
        Boolean hasKey = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(hasKey)) {
            Set<String> cached = redisTemplate.opsForSet().members(key);
            if (cached != null) {
                return cached;
            }
        }

        // 2. 缓存未命中，从数据库加载
        Set<String> permissions = loadFromDb(userId);

        // 3. 写入 Redis 并设置过期时间
        try {
            if (!permissions.isEmpty()) {
                redisTemplate.opsForSet().add(key, permissions.toArray(new String[0]));
            } else {
                // 空权限也缓存，避免缓存穿透
                redisTemplate.opsForSet().add(key, "");
            }
            redisTemplate.expire(key, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入权限缓存失败（不影响当前请求）: userId={}", userId, e);
        }

        // 移除占位空字符串
        permissions.remove("");
        return permissions;
    }

    @Override
    public void refresh(Long userId) {
        if (userId == null) {
            return;
        }
        String key = buildKey(userId);
        try {
            redisTemplate.delete(key);
            log.debug("已刷新权限缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("刷新权限缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 从数据库加载权限编码集合。
     *
     * @param userId 用户 ID
     * @return 权限编码集合
     */
    private Set<String> loadFromDb(Long userId) {
        Set<String> permissions = new HashSet<>();

        List<Long> roleIds = adminAccountRoleMapper.selectRoleIdsByAdminAccountId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            List<String> permissionCodes = rolePermissionMapper.selectPermissionCodesByRoleIds(roleIds);
            if (permissionCodes != null) {
                permissions.addAll(permissionCodes);
            }
        }

        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account != null && account.getLevel() != null) {
            if (account.getLevel() == 2) {
                permissions.addAll(TENANT_ADMIN_DEFAULT_PERMISSIONS);
            } else if (account.getLevel() == 3) {
                permissions.addAll(BOOTH_ADMIN_DEFAULT_PERMISSIONS);
            }
        }

        return permissions;
    }

    /**
     * 构建 Redis Key。
     *
     * @param userId 用户 ID
     * @return Redis Key
     */
    private String buildKey(Long userId) {
        return PERMISSION_KEY_PREFIX + userId;
    }
}
