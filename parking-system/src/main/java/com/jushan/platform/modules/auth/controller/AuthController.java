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

        // 加载角色编码
        List<String> roleCodes = loadRoleCodes(account.getId());
        log.info("加载角色编码结果: adminAccountId={}, roleCodes={}", account.getId(), roleCodes);
        String rolesJson = roleCodes.isEmpty() ? null : "[" + roleCodes.stream()
                .map(r -> "\"" + r + "\"")
                .collect(Collectors.joining(",")) + "]";

        // 生成 Token
        String userType = account.getTenantId() == null ? USER_TYPE_PLATFORM : USER_TYPE_TENANT;
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
        String userType = account.getTenantId() == null ? USER_TYPE_PLATFORM : USER_TYPE_TENANT;

        String newToken = JwtUtils.generateToken(account.getId(), account.getTenantId(), userType,
                null, permissionsStr, jwtSecret, jwtExpiration);

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
        return userInfo;
    }
}
