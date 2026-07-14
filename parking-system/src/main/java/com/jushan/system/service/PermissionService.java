package com.jushan.system.service;

import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.LegacySysRolePermissionMapper;
import com.jushan.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 权限查询服务。
 * <p>
 * 负责根据用户 ID 加载角色和权限列表，供 {@link StpInterfaceImpl} 调用。
 * <p>
 * 权限数据在登录时写入 Sa-Token Session，后续权限校验从 Session 读取，
 * 避免每次 {@code checkPermission()} 都查询数据库。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final SysUserMapper sysUserMapper;
    private final LegacySysRolePermissionMapper legacyRolePermissionMapper;

    public PermissionService(SysUserMapper sysUserMapper,
                             LegacySysRolePermissionMapper legacyRolePermissionMapper) {
        this.sysUserMapper = sysUserMapper;
        this.legacyRolePermissionMapper = legacyRolePermissionMapper;
    }

    /**
     * 根据用户 ID 查询其角色编码列表。
     *
     * @param loginId 用户 ID（即 Sa-Token 的 loginId）
     * @return 角色编码列表
     */
    public List<String> getRoleCodes(Object loginId) {
        SysUser user = findUser(loginId);
        if (user == null) {
            return Collections.emptyList();
        }
        return parseRoles(user.getRoles());
    }

    /**
     * 根据用户 ID 查询其权限编码列表。
     * <p>先查角色，再通过角色查权限，结果去重。
     *
     * @param loginId 用户 ID
     * @return 权限编码列表
     */
    public List<String> getPermissionCodes(Object loginId) {
        List<String> roleCodes = getRoleCodes(loginId);
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return legacyRolePermissionMapper.selectPermissionCodesByRoleCodes(roleCodes);
        } catch (Exception e) {
            log.error("查询权限失败: loginId={}, roles={}", loginId, roleCodes, e);
            return Collections.emptyList();
        }
    }

    private SysUser findUser(Object loginId) {
        try {
            long userId = Long.parseLong(String.valueOf(loginId));
            return sysUserMapper.selectByIdIgnoreTenant(userId);
        } catch (Exception e) {
            log.warn("根据 loginId 查询用户失败: loginId={}", loginId, e);
            return null;
        }
    }

    /**
     * 解析 roles JSON 数组字段。
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
                return List.of(inner.replace("\"", "").split("\\s*,\\s*"));
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("解析 roles 字段失败: {}", rolesJson, e);
            return Collections.emptyList();
        }
    }
}
