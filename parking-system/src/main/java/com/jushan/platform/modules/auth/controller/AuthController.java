package com.jushan.platform.modules.auth.controller;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.JwtUtils;
import com.jushan.platform.infra.security.PermissionProvider;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.mapper.SysAdminAccountRoleMapper;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import com.jushan.platform.modules.account.service.SysAdminAccountService;
import com.jushan.system.mapper.SysRoleMapper;
import com.jushan.platform.modules.auth.dto.LoginRequest;
import com.jushan.platform.modules.auth.vo.LoginResult;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证控制器（JWT 版本）。
 * <p>
 * 提供基于 JWT 的登录、刷新 Token、获取用户信息、登出接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController("jwtAuthController")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** 平台用户类型 */
    private static final String USER_TYPE_PLATFORM = "platform";
    /** 租户用户类型 */
    private static final String USER_TYPE_TENANT = "tenant";
    /** 岗亭管理员用户类型（跨租户） */
    private static final String USER_TYPE_BOOTH = "booth";
    /** 岗亭管理员级别 */
    private static final int LEVEL_LOT = 3;

    /** 账号状态：正常（启用） */
    private static final int STATUS_NORMAL = 1;
    /** 账号状态：禁用 */
    private static final int STATUS_DISABLED = 0;
    /** 账号状态：锁定 */
    private static final int STATUS_LOCKED = 2;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private final SysAdminAccountMapper adminAccountMapper;
    private final SysAdminAccountRoleMapper adminAccountRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysAdminAccountService adminAccountService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PermissionProvider permissionProvider;

    public AuthController(SysAdminAccountMapper adminAccountMapper,
                          SysAdminAccountRoleMapper adminAccountRoleMapper,
                          SysRoleMapper sysRoleMapper,
                          SysAdminAccountService adminAccountService,
                          BCryptPasswordEncoder passwordEncoder,
                          PermissionProvider permissionProvider) {
        this.adminAccountMapper = adminAccountMapper;
        this.adminAccountRoleMapper = adminAccountRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.adminAccountService = adminAccountService;
        this.passwordEncoder = passwordEncoder;
        this.permissionProvider = permissionProvider;
    }

    /**
     * 用户登录。
     */
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername().trim();
        SysAdminAccount account = adminAccountMapper.selectByUsernameIgnoreTenant(username);

        if (account == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 校验账号状态
        Integer status = account.getStatus();
        if (STATUS_DISABLED == status) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "账号已被禁用");
        }
        if (STATUS_LOCKED == status) {
            LocalDateTime lockUntil = account.getLockUntil();
            if (lockUntil != null && lockUntil.isAfter(LocalDateTime.now())) {
                throw new BusinessException(CommonErrorCode.FORBIDDEN,
                        "账号已锁定，请 " + lockUntil + " 后再试");
            }
            // 锁定期已过，自动解锁
            account.setStatus(STATUS_NORMAL);
            account.setLockUntil(null);
            account.setLoginFailCount(0);
        }

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            adminAccountService.onLoginFail(username);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 登录成功
        adminAccountService.onLoginSuccess(username);

        // 加载权限
        Set<String> permissions = permissionProvider.getPermissions(account.getId());
        String permissionsStr = permissions.stream()
                .filter(p -> !p.isEmpty())
                .collect(Collectors.joining(","));

        // 加载角色编码（岗亭管理员确保包含前端路由角色 'booth'）
        String userType = resolveUserType(account);
        String rolesJson = buildRolesJson(account.getId(), userType);
        log.info("加载角色编码结果: adminAccountId={}, rolesJson={}", account.getId(), rolesJson);

        // 生成 Token
        String token = JwtUtils.generateToken(account.getId(), account.getTenantId(), userType,
                rolesJson, permissionsStr, jwtSecret, jwtExpiration);
        String refreshToken = JwtUtils.generateToken(account.getId(), account.getTenantId(), userType,
                rolesJson, permissionsStr, jwtSecret, refreshExpiration);

        LoginResult result = new LoginResult();
        result.setToken(token);
        result.setRefreshToken(refreshToken);
        result.setUserInfo(buildUserInfo(account));
        result.setPermissions(List.copyOf(permissions));

        log.info("管理员登录成功: userId={}, username={}, level={}",
                account.getId(), account.getUsername(), account.getLevel());
        return R.ok(result);
    }

    /**
     * 刷新 Access Token。
     */
    @PostMapping("/refresh")
    public R<LoginResult> refresh(@RequestBody LoginResult request) {
        String refreshToken = request.getRefreshToken();
        if (!JwtUtils.validateToken(refreshToken, jwtSecret)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Refresh Token 无效或已过期");
        }

        Claims claims = JwtUtils.parseClaims(refreshToken, jwtSecret);
        Long userId = JwtUtils.getUserIdFromClaims(claims);

        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account == null || account.getStatus() == STATUS_DISABLED) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号不存在或已被禁用");
        }

        Set<String> permissions = permissionProvider.getPermissions(account.getId());
        String permissionsStr = permissions.stream()
                .filter(p -> !p.isEmpty())
                .collect(Collectors.joining(","));
        String userType = resolveUserType(account);
        String rolesJson = buildRolesJson(account.getId(), userType);

        String newToken = JwtUtils.generateToken(account.getId(), account.getTenantId(), userType,
                rolesJson, permissionsStr, jwtSecret, jwtExpiration);

        LoginResult result = new LoginResult();
        result.setToken(newToken);
        result.setRefreshToken(refreshToken);
        result.setUserInfo(buildUserInfo(account));
        result.setPermissions(List.copyOf(permissions));

        return R.ok(result);
    }

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/userinfo")
    public R<LoginResult.UserInfo> userinfo() {
        Long userId = TenantContext.requireUserId();
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录已失效");
        }
        return R.ok(buildUserInfo(account));
    }

    /**
     * 获取当前会话信息。
     * <p>
     * 返回当前登录用户的完整会话信息，包括用户基本信息、权限。
     * 代操作功能已废弃，超级管理员可直接操作任意租户数据。
     */
    @GetMapping("/session")
    public R<SessionInfo> session() {
        Long userId = TenantContext.requireUserId();
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录已失效");
        }

        SessionInfo session = new SessionInfo();
        session.setUserId(account.getId());
        session.setUsername(account.getUsername());
        session.setRealName(account.getRealName());
        session.setLevel(account.getLevel());
        session.setTenantId(account.getTenantId());

        // 加载权限
        Set<String> permissions = permissionProvider.getPermissions(account.getId());
        session.setPermissions(List.copyOf(permissions));

        return R.ok(session);
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        Long userId = TenantContext.getUserId();
        if (userId != null) {
            permissionProvider.refresh(userId);
        }
        return R.ok();
    }

    /**
     * 根据管理员级别和租户归属解析用户类型。
     * <ul>
     *   <li>level=1, tenantId=null → platform（超级管理员）</li>
     *   <li>level=3, tenantId=null → booth（岗亭管理员，跨租户）</li>
     *   <li>tenantId!=null → tenant（租户/公司管理员）</li>
     * </ul>
     */
    private String resolveUserType(SysAdminAccount account) {
        if (account.getTenantId() == null) {
            if (account.getLevel() != null && account.getLevel() == LEVEL_LOT) {
                return USER_TYPE_BOOTH;
            }
            return USER_TYPE_PLATFORM;
        }
        return USER_TYPE_TENANT;
    }

    /**
     * 构建 JWT roles 声明（JSON 数组字符串）。
     * <p>
     * 岗亭管理员（userType=booth）确保包含前端路由角色 {@code "booth"}——
     * 前端路由守卫依赖该值，而 sys_role 中的角色编码（如 booth_operator）不含此值。
     *
     * @param adminAccountId 管理员账号 ID
     * @param userType       用户类型
     * @return roles JSON 字符串，无角色时为 null
     */
    private String buildRolesJson(Long adminAccountId, String userType) {
        List<String> roleCodes = loadRoleCodes(adminAccountId);
        if (USER_TYPE_BOOTH.equals(userType) && !roleCodes.contains(USER_TYPE_BOOTH)) {
            roleCodes = new java.util.ArrayList<>(roleCodes);
            roleCodes.add(USER_TYPE_BOOTH);
        }
        if (roleCodes.isEmpty()) {
            return null;
        }
        return "[" + roleCodes.stream()
                .map(r -> "\"" + r + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * 加载当前管理员账号的角色编码列表。
     *
     * @param adminAccountId 管理员账号 ID
     * @return 角色编码列表（不为 null）
     */
    private List<String> loadRoleCodes(Long adminAccountId) {
        try {
            List<Long> roleIds = adminAccountRoleMapper.selectRoleIdsByAdminAccountId(adminAccountId);
            if (roleIds == null || roleIds.isEmpty()) {
                return Collections.emptyList();
            }
            // 通过 sys_role 表查询角色编码（使用 MyBatis-Plus 基础查询）
            return roleIds.stream()
                    .map(roleId -> {
                        try {
                            // 使用 SysRoleMapper 查询角色编码
                            com.jushan.system.entity.SysRole role = sysRoleMapper.selectById(roleId);
                            return role != null ? role.getCode() : null;
                        } catch (Exception ex) {
                            log.warn("查询角色编码失败: roleId={}", roleId, ex);
                            return null;
                        }
                    })
                    .filter(code -> code != null && !code.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("加载角色编码失败: adminAccountId={}", adminAccountId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建用户信息。
     */
    private LoginResult.UserInfo buildUserInfo(SysAdminAccount account) {
        LoginResult.UserInfo userInfo = new LoginResult.UserInfo();
        userInfo.setUserId(account.getId());
        userInfo.setUsername(account.getUsername());
        userInfo.setRealName(account.getRealName());
        userInfo.setLevel(account.getLevel());
        userInfo.setTenantId(account.getTenantId());
        userInfo.setMustChangePassword(account.getMustChangePassword());
        return userInfo;
    }

    /**
     * 会话信息视图。
     */
    public static class SessionInfo {
        private Long userId;
        private String username;
        private String realName;
        private Integer level;
        private Long tenantId;
        private List<String> permissions;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }

        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

        public List<String> getPermissions() { return permissions; }
        public void setPermissions(List<String> permissions) { this.permissions = permissions; }
    }
}
