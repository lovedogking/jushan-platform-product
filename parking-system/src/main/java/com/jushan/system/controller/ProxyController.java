package com.jushan.system.controller;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.framework.auth.DataScope;
import com.jushan.platform.infra.security.JwtUtils;
import com.jushan.platform.modules.account.entity.SysAdminAccount;
import com.jushan.platform.modules.account.mapper.SysAdminAccountMapper;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 代操作（代理）管理控制器。
 * <p>
 * 平台管理员通过此接口进入指定租户的代操作模式，临时获得该租户的管理权限。
 * 代操作期间的所有行为会被标记为代操作并记录审计日志。
 * <p>
 * 权限：仅限平台用户（super_admin、platform_operator）
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/proxy")
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    /** Redis key 前缀：代操作原始 token 缓存 */
    private static final String PROXY_ORIGINAL_TOKEN_KEY = "proxy:original_token:";
    /** Redis key 前缀：代操作状态缓存 */
    private static final String PROXY_STATUS_KEY = "proxy:status:";
    /** 代操作 token 过期时间（小时） */
    private static final int PROXY_TOKEN_EXPIRY_HOURS = 2;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    private final StringRedisTemplate redisTemplate;
    private final SysAdminAccountMapper adminAccountMapper;
    private final TenantMapper tenantMapper;
    private final AuditService auditService;

    public ProxyController(StringRedisTemplate redisTemplate,
                           SysAdminAccountMapper adminAccountMapper,
                           TenantMapper tenantMapper,
                           AuditService auditService) {
        this.redisTemplate = redisTemplate;
        this.adminAccountMapper = adminAccountMapper;
        this.tenantMapper = tenantMapper;
        this.auditService = auditService;
    }

    // ==================== 代操作启动/停止 ====================

    /**
     * 启动代操作模式。
     * <p>
     * 平台管理员输入目标租户 ID 和代操作原因，系统生成一个带有目标租户 ID 的临时 Token。
     * 原 Token 被缓存到 Redis，停止代操作后恢复。
     * <p>
     * 权限：仅限平台用户
     *
     * @param request 代操作启动请求
     * @return 新的临时 Token
     */
    @PostMapping("/start")
    public R<ProxyStartResult> startProxy(@Valid @RequestBody StartProxyRequest request) {
        // 1. 校验平台用户身份
        DataScope.requirePlatformUser();
        Long userId = TenantContext.requireUserId();

        // 2. 检查是否已在代操作模式
        String statusKey = PROXY_STATUS_KEY + userId;
        String existing = redisTemplate.opsForValue().get(statusKey);
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "已是代操作模式，请先停止当前代操作");
        }

        // 3. 校验目标租户
        Tenant tenant = tenantMapper.selectById(request.getTenantId());
        if (tenant == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "目标租户不存在");
        }
        if (!"ENABLED".equals(tenant.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "租户不存在或已被禁用");
        }

        // 4. 获取当前用户角色和权限
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        if (account == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "登录已失效");
        }

        // 加载角色和权限（简化处理：复用现有权限）
        String roles = TenantContext.getRoles();
        String permissions = TenantContext.getPermissions();

        // 5. 生成代操作 Token（将 tenantId 设为目标租户）
        String proxyToken = JwtUtils.generateToken(userId, request.getTenantId(),
                TenantContext.USER_TYPE_TENANT, roles, permissions, jwtSecret, jwtExpiration);

        // 6. 缓存原 Token 和代操作状态到 Redis
        String originalToken = extractCurrentToken();
        String originalTokenKey = PROXY_ORIGINAL_TOKEN_KEY + userId;
        redisTemplate.opsForValue().set(originalTokenKey, originalToken != null ? originalToken : "",
                Duration.ofHours(PROXY_TOKEN_EXPIRY_HOURS));
        redisTemplate.opsForValue().set(statusKey, String.valueOf(request.getTenantId()),
                Duration.ofHours(PROXY_TOKEN_EXPIRY_HOURS));

        // 7. 记录审计日志
        auditService.logProxyStart(userId, account.getUsername(), request.getTenantId(), request.getReason());

        log.info("启动代操作成功: userId={}, targetTenantId={}, reason={}",
                userId, request.getTenantId(), request.getReason());

        ProxyStartResult result = new ProxyStartResult();
        result.setToken(proxyToken);
        result.setTenantId(request.getTenantId());
        result.setTenantName(tenant.getName());
        return R.ok(result);
    }

    /**
     * 停止代操作模式。
     * <p>
     * 恢复原始平台 Token，清除 Redis 中的代操作状态。
     * <p>
     * 权限：仅限平台用户
     *
     * @return 原始 Token
     */
    @PostMapping("/stop")
    public R<ProxyStopResult> stopProxy() {
        // 1. 校验平台用户身份
        DataScope.requirePlatformUser();
        Long userId = TenantContext.requireUserId();

        // 2. 检查是否在代操作模式
        String statusKey = PROXY_STATUS_KEY + userId;
        String proxyTenantId = redisTemplate.opsForValue().get(statusKey);
        if (proxyTenantId == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "未处于代操作模式");
        }

        // 3. 恢复原始 Token
        String originalTokenKey = PROXY_ORIGINAL_TOKEN_KEY + userId;
        String originalToken = redisTemplate.opsForValue().get(originalTokenKey);

        // 4. 清除 Redis 缓存
        redisTemplate.delete(originalTokenKey);
        redisTemplate.delete(statusKey);

        // 5. 记录审计日志
        SysAdminAccount account = adminAccountMapper.selectByIdIgnoreTenant(userId);
        auditService.logProxyStop(userId, account != null ? account.getUsername() : "unknown",
                Long.parseLong(proxyTenantId));

        log.info("停止代操作成功: userId={}, restoredTenantId={}", userId, proxyTenantId);

        ProxyStopResult result = new ProxyStopResult();
        result.setToken(originalToken);
        return R.ok(result);
    }

    /**
     * 获取当前代操作状态。
     * <p>
     * 权限：仅限平台用户
     *
     * @return 代操作状态
     */
    @GetMapping("/status")
    public R<ProxyStatusVO> getProxyStatus() {
        DataScope.requirePlatformUser();
        Long userId = TenantContext.requireUserId();

        String statusKey = PROXY_STATUS_KEY + userId;
        String proxyTenantId = redisTemplate.opsForValue().get(statusKey);

        ProxyStatusVO vo = new ProxyStatusVO();
        if (proxyTenantId != null) {
            vo.setActive(true);
            vo.setTargetTenantId(Long.parseLong(proxyTenantId));
            Tenant tenant = tenantMapper.selectById(vo.getTargetTenantId());
            if (tenant != null) {
                vo.setTargetTenantName(tenant.getName());
            }
        } else {
            vo.setActive(false);
        }
        return R.ok(vo);
    }

    // ==================== 内部方法 ====================

    /**
     * 从当前请求头提取 Token（简化实现，实际应由前端传入或从上下文获取）。
     * 由于当前实现为无状态 JWT，此处返回空字符串作为占位。
     */
    private String extractCurrentToken() {
        // 当前为无状态 JWT 架构，原始 token 由前端保存并传入
        // 代操作模式下前端应使用返回的新 token，停止时重新使用旧 token
        return "";
    }

    // ==================== DTO / VO ====================

    /**
     * 启动代操作请求。
     */
    public static class StartProxyRequest {
        @NotBlank(message = "目标租户 ID 不能为空")
        private Long tenantId;

        @NotBlank(message = "代操作原因不能为空")
        private String reason;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * 启动代操作结果。
     */
    public static class ProxyStartResult {
        private String token;
        private Long tenantId;
        private String tenantName;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    }

    /**
     * 停止代操作结果。
     */
    public static class ProxyStopResult {
        private String token;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    /**
     * 代操作状态视图。
     */
    public static class ProxyStatusVO {
        private boolean active;
        private Long targetTenantId;
        private String targetTenantName;
        private String reason;
        private String startedAt;

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public Long getTargetTenantId() { return targetTenantId; }
        public void setTargetTenantId(Long targetTenantId) { this.targetTenantId = targetTenantId; }

        public String getTargetTenantName() { return targetTenantName; }
        public void setTargetTenantName(String targetTenantName) { this.targetTenantName = targetTenantName; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    }
}
