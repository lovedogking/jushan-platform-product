package com.jushan.system.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限加载接口实现。
 * <p>
 * 实现 {@link StpInterface}，为 Sa-Token 的 {@code StpUtil.checkPermission()}、
 * {@code StpUtil.checkRole()}、{@code @SaCheckPermission}、{@code @SaCheckRole}
 * 提供角色和权限数据。
 * <p>
 * <strong>缓存策略</strong>：首次加载后存入 Sa-Token Session，
 * 后续调用从 Session 读取，避免每次鉴权都查询数据库。
 * 用户重新登录时 Session 重建，权限自然刷新。
 * <p>
 * Sa-Token 自动发现本实现（通过 Spring Bean 扫描 {@link StpInterface} 类型），
 * 无需额外配置。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private static final Logger log = LoggerFactory.getLogger(StpInterfaceImpl.class);

    private static final String SESSION_ROLES_KEY = "stp:roles";
    private static final String SESSION_PERMISSIONS_KEY = "stp:permissions";

    private final PermissionService permissionService;

    public StpInterfaceImpl(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 先从 Session 缓存读取
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session != null) {
            Object cached = session.get(SESSION_PERMISSIONS_KEY);
            if (cached instanceof List<?> list && !list.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> permissions = (List<String>) list;
                return permissions;
            }
        }

        // 缓存未命中，从数据库加载
        List<String> permissions = permissionService.getPermissionCodes(loginId);
        if (session != null) {
            session.set(SESSION_PERMISSIONS_KEY, permissions);
        }

        if (log.isDebugEnabled()) {
            log.debug("加载权限: loginId={}, count={}", loginId, permissions.size());
        }
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 先从 Session 缓存读取
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session != null) {
            Object cached = session.get(SESSION_ROLES_KEY);
            if (cached instanceof List<?> list && !list.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) list;
                return roles;
            }
        }

        // 缓存未命中，从数据库加载
        List<String> roles = permissionService.getRoleCodes(loginId);
        if (session != null) {
            session.set(SESSION_ROLES_KEY, roles);
        }

        if (log.isDebugEnabled()) {
            log.debug("加载角色: loginId={}, roles={}", loginId, roles);
        }
        return roles;
    }

    /**
     * 清除指定用户的权限缓存（角色变更后调用）。
     * <p>目前第一阶段只有固定角色，不需要调用。后续支持动态角色后再启用。
     *
     * @param loginId 用户 ID
     */
    public void clearCache(Object loginId) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session != null) {
            session.delete(SESSION_ROLES_KEY);
            session.delete(SESSION_PERMISSIONS_KEY);
        }
    }
}
