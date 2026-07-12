package com.jushan.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.redis.RedisKeyPrefix;
import com.jushan.framework.web.LogDesensitize;
import com.jushan.system.dto.LoginRequest;
import com.jushan.system.dto.LoginResult;
import com.jushan.system.entity.SysLoginLog;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.SysLoginLogMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.vo.LoginUserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;


/**
 * 认证服务。
 * <p>
 * 负责登录校验、密码验证、限流、会话管理和登录审计日志。
 * <p>
 * Redis 限流为可选依赖：当 {@link StringRedisTemplate} 不可用时（如测试环境），
 * 限流功能自动降级，不影响登录主流程。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 登录失败最大次数 */
    private static final int MAX_LOGIN_FAILS = 5;

    /** 失败计数窗口（分钟） */
    private static final long FAIL_WINDOW_MINUTES = 15;

    /** 登录失败限制锁定描述 */
    private static final String RATE_LIMIT_REASON = "登录失败次数过多，请15分钟后重试";

    private final SysUserMapper sysUserMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final PermissionService permissionService;

    /**
     * AuditService 为可选依赖（AuditService 在 parking-system 内定义，
     * 此处注入可能产生循环引用，因此通过构造器注入并允许可选）。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditService auditService;

    /** Redis 限流（可选依赖，不可用时降级） */
    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** Sa-Token 超时秒数（与 sa-token.timeout 配置保持一致） */
    @Value("${sa-token.timeout:86400}")
    private long tokenTimeoutSeconds;

    public AuthService(SysUserMapper sysUserMapper,
                      SysLoginLogMapper sysLoginLogMapper,
                      PermissionService permissionService) {
        this.sysUserMapper = sysUserMapper;
        this.sysLoginLogMapper = sysLoginLogMapper;
        this.permissionService = permissionService;
    }

    /**
     * 用户登录。
     *
     * @param request 登录请求（含明文密码）
     * @param ip      客户端 IP
     * @param ua      客户端 User-Agent
     * @return 登录结果（Token + 用户信息）
     * @throws BusinessException 登录失败时抛出
     */
    public LoginResult login(LoginRequest request, String ip, String ua) {
        String username = request.getUsername();

        // 1. 限流检查
        checkRateLimit(username, ip);

        // 2. 查找用户
        SysUser user = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));

        if (user == null) {
            recordLoginLog(null, username, ip, ua, "FAIL_BAD_CREDENTIALS", "账号不存在");
            incrementFailCount(username, ip);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 3. 账号状态检查
        if ("DISABLED".equals(user.getStatus())) {
            recordLoginLog(user.getId(), username, ip, ua, "FAIL_DISABLED", "账号已禁用");
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号已被禁用，请联系管理员");
        }
        if ("LOCKED".equals(user.getStatus())) {
            recordLoginLog(user.getId(), username, ip, ua, "FAIL_DISABLED", "账号已锁定");
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号已被锁定，请联系管理员");
        }
        if ("PENDING_REVIEW".equals(user.getStatus())) {
            recordLoginLog(user.getId(), username, ip, ua, "FAIL_DISABLED", "账号待审核");
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号尚未通过审核，请等待管理员审核");
        }

        // 4. 密码校验
        if (!BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            recordLoginLog(user.getId(), username, ip, ua, "FAIL_BAD_CREDENTIALS", "密码错误");
            incrementFailCount(username, ip);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 5. Sa-Token 登录，同时保存租户上下文到 User-Session（供 TenantContextInterceptor 读取）
        StpUtil.login(user.getId());
        StpUtil.getSession().set("device", "web");

        // 将租户上下文写入 User-Session（按 loginId 索引），与 getSessionByLoginId 一致
        // TenantContextInterceptor 通过 getSessionByLoginId 读取同一会话
        cn.dev33.satoken.session.SaSession userSession = StpUtil.getSessionByLoginId(user.getId());

        // 代理状态存储在 Token-Session（每个 Token 独立），重新登录自动获得新 Token-Session，
        // 因此无需显式清理代理状态。

        if (user.getTenantId() != null) {
            userSession.set(com.jushan.framework.auth.TenantContext.SESSION_KEY_TENANT_ID,
                    user.getTenantId());
        }
        userSession.set(com.jushan.framework.auth.TenantContext.SESSION_KEY_USER_TYPE,
                user.getTenantId() != null
                        ? com.jushan.framework.auth.TenantContext.USER_TYPE_TENANT
                        : com.jushan.framework.auth.TenantContext.USER_TYPE_PLATFORM);
        if (user.getRoles() != null) {
            userSession.set(com.jushan.framework.auth.TenantContext.SESSION_KEY_ROLES,
                    user.getRoles());
        }

        String tokenValue = StpUtil.getTokenValue();

        // 6. 记录登录成功日志
        recordLoginLog(user.getId(), username, ip, ua, "SUCCESS", "");
        clearFailCount(username, ip);

        // 7. 构建返回
        List<String> roles = parseRoles(user.getRoles());
        List<String> permissions = permissionService.getPermissionCodes(user.getId());
        LoginUserVo userVo = new LoginUserVo(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getDisplayName(),
                roles,
                permissions
        );

        // 8. 构建返回，包含凭据状态（FIX-04）
        LoginResult result = new LoginResult(tokenValue, tokenTimeoutSeconds, userVo);
        result.setCredentialStatus(user.getCredentialStatus() != null
                ? user.getCredentialStatus() : "ACTIVE");

        log.info("用户 {}（{}）登录成功, credentialStatus={}",
                LogDesensitize.maskPhone(username), ip, result.getCredentialStatus());
        return result;
    }

    /**
     * 退出登录。
     * <p>
     * 调用 Sa-Token 登出，后续请求的 Token 立即失效。
     */
    public void logout() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId != null) {
                log.info("用户 ID={} 退出登录", loginId);
            }
            StpUtil.logout();
        } catch (Exception e) {
            log.debug("退出登录时无有效会话: {}", e.getMessage());
        }
    }

    /**
     * 修改当前用户密码（FIX-04）。
     * <p>
     * 验证旧密码后更新为新密码，并将 credential_status 设为 ACTIVE。
     * 修改成功后注销当前会话，强制使用新密码重新登录。
     *
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文）
     * @throws BusinessException 旧密码错误或用户不存在
     */
    public void changePassword(String oldPassword, String newPassword) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "用户不存在");
        }

        // 验证旧密码
        if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "旧密码错误");
        }

        // 更新密码和凭据状态
        user.setPasswordHash(BCrypt.hashpw(newPassword));
        user.setCredentialStatus("ACTIVE");
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        // 注销所有现有会话，强制使用新密码重新登录
        StpUtil.logout(userId);

        log.info("用户 {} 修改密码成功，凭据状态更新为 ACTIVE", userId);
    }

    /**
     * 获取当前会话用户信息。
     *
     * @return 当前登录用户视图（含租户信息和代理状态）
     * @throws BusinessException 未登录时抛出
     */
    public LoginUserVo getSessionUser() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            // 会话有效但用户被物理删除（极端情况）
            StpUtil.logout();
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "用户不存在");
        }

        List<String> roles = parseRoles(user.getRoles());
        List<String> permissions = permissionService.getPermissionCodes(user.getId());
        LoginUserVo vo = new LoginUserVo(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getDisplayName(),
                roles,
                permissions
        );
        // 返回当前上下文中的租户 ID（代理模式下为目标租户 ID）
        vo.setTenantId(com.jushan.framework.auth.TenantContext.getTenantId());
        // 返回代理状态（前端据此显示"平台代操作"标识）
        if (auditService != null) {
            vo.setProxy(auditService.getProxyStatus());
        }
        return vo;
    }

    // ==================== 限流 ====================

    /**
     * 检查登录失败次数。
     * <p>
     * Redis Key：{@code auth:login-fail:<username>:<ip>}
     */
    private void checkRateLimit(String username, String ip) {
        if (stringRedisTemplate == null) {
            return; // Redis 不可用时降级，不限流
        }
        String key = RedisKeyPrefix.LOGIN_FAIL + username + ":" + ip;
        try {
            String countStr = stringRedisTemplate.opsForValue().get(key);
            int count = countStr != null ? Integer.parseInt(countStr) : 0;
            if (count >= MAX_LOGIN_FAILS) {
                recordLoginLog(null, username, ip, null, "FAIL_RATE_LIMIT", RATE_LIMIT_REASON);
                log.warn("登录限流触发：username={}, ip={}, count={}", username, ip, count);
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED, RATE_LIMIT_REASON);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用时降级：允许登录，记录 WARN
            log.warn("Redis 不可用，跳过登录限流检查: {}", e.getMessage());
        }
    }

    private void incrementFailCount(String username, String ip) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = RedisKeyPrefix.LOGIN_FAIL + username + ":" + ip;
        try {
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, Duration.ofMinutes(FAIL_WINDOW_MINUTES));
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过失败计数: {}", e.getMessage());
        }
    }

    private void clearFailCount(String username, String ip) {
        if (stringRedisTemplate == null) {
            return;
        }
        String key = RedisKeyPrefix.LOGIN_FAIL + username + ":" + ip;
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，跳过清除失败计数: {}", e.getMessage());
        }
    }

    // ==================== 审计日志 ====================

    private void recordLoginLog(Long userId, String username, String ip, String ua,
                                String result, String failReason) {
        try {
            SysLoginLog logEntry = new SysLoginLog();
            logEntry.setUserId(userId);
            logEntry.setUsername(username);
            logEntry.setIp(ip != null ? ip : "");
            logEntry.setUserAgent(ua != null ? ua : "");
            logEntry.setResult(result);
            logEntry.setFailReason(failReason);
            logEntry.setCreatedAt(LocalDateTime.now());
            sysLoginLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 审计日志写入失败不能阻断登录主流程
            log.error("登录日志记录失败: username={}, result={}", username, result, e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 解析 roles JSON 数组字段。
     */
    private List<String> parseRoles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // 简单 JSON 数组解析（不引入 Jackson 依赖）
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
