package com.jushan.system.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.framework.auth.TenantContext;
import com.jushan.system.entity.SysAuditLog;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.SysAuditLogMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.vo.AuditLogVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 审计服务。
 * <p>
 * 负责超级管理员代操作（代理模式启动/停止）和通用高风险操作审计日志写入。
 * <p>
 * <strong>代理模式设计</strong>：
 * <ol>
 *   <li>超级管理员调用 startProxy，将代理状态写入 Sa-Token User-Session</li>
 *   <li>TenantContextFilter 感知代理状态，将 tenantId 替换为目标租户 ID</li>
 *   <li>TenantContext.isProxyMode() 返回 true，所有写操作感知代理模式</li>
 *   <li>业务代码调用 writeAuditLog 记录真实操作人和目标租户</li>
 *   <li>超级管理员调用 stopProxy 退出代理模式</li>
 * </ol>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** 操作结果 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_UNCERTAIN = "UNCERTAIN";

    /** 操作类型 */
    public static final String ACTION_PROXY_START = "proxy_start";
    public static final String ACTION_PROXY_STOP = "proxy_stop";

    /** 目标类型 */
    public static final String TARGET_TYPE_TENANT = "tenant";

    private final TenantMapper tenantMapper;
    private final SysUserMapper sysUserMapper;
    private final SysAuditLogMapper sysAuditLogMapper;

    public AuditService(TenantMapper tenantMapper,
                       SysUserMapper sysUserMapper,
                       SysAuditLogMapper sysAuditLogMapper) {
        this.tenantMapper = tenantMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysAuditLogMapper = sysAuditLogMapper;
    }

    // ==================== 代操作（代理模式） ====================

    /**
     * 启动代操作模式。
     * <p>
     * 仅平台用户（超级管理员/平台运营）可启动。将代理状态写入当前用户的 Sa-Token User-Session，
     * 后续请求中 TenantContextFilter 会据此构建代理上下文。
     *
     * @param targetTenantId 目标租户 ID
     * @param reason         操作原因
     * @param clientIp       客户端 IP
     */
    public void startProxy(Long targetTenantId, String reason, String clientIp) {
        // 1. 先检查是否已在代理模式（在 requirePlatformUser 之前，因为代理模式下
        //    isPlatformUser() 返回 false，会误判为 403）
        if (TenantContext.isProxyMode()) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "已是代操作模式，请先退出当前代操作再切换");
        }

        // 2. 校验当前用户是平台用户
        DataScope.requirePlatformUser();

        // 3. 校验当前用户是超级管理员（不是 platform_operator）
        // 使用 DataScope.hasRole() 做 JSON 数组精确匹配，防止子串误判（FIX-05）
        if (!DataScope.hasRole("super_admin")) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "仅超级管理员可启动代操作模式");
        }

        // 4. 校验目标租户存在且已启用
        Tenant targetTenant = Optional.ofNullable(tenantMapper.selectById(targetTenantId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "目标租户不存在"));
        DataScope.validateTenantEnabled(targetTenant.getStatus());

        // 5. 获取操作人信息
        long operatorId = StpUtil.getLoginIdAsLong();
        SysUser operator = Optional.ofNullable(sysUserMapper.selectById(operatorId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "操作人信息异常"));
        String operatorName = operator.getDisplayName() != null ? operator.getDisplayName() : operator.getUsername();

        // 6. 将代理状态写入 Token-Session（绑定当前 Token，不同 Token 互不影响）
        SaSession tokenSession = StpUtil.getSession();
        tokenSession.set(TenantContext.SESSION_KEY_PROXY_TENANT_ID, targetTenantId);
        tokenSession.set(TenantContext.SESSION_KEY_PROXY_OPERATOR_ID, operatorId);
        tokenSession.set(TenantContext.SESSION_KEY_PROXY_OPERATOR_NAME, operatorName);

        // 7. 写入审计日志（fail-close：审计失败时操作不允许执行）
        writeAuditLogDirect(
                targetTenantId,
                TARGET_TYPE_TENANT,
                String.valueOf(targetTenantId),
                ACTION_PROXY_START,
                operatorId,
                operatorName,
                targetTenantId,
                1,
                null,
                null,
                RESULT_SUCCESS,
                "",
                reason,
                clientIp,
                true  // fail-close: 代理操作必须在有审计的前提下执行
        );

        log.info("超级管理员 {}（ID={}）启动代操作模式，目标租户: {}（ID={}）",
                operatorName, operatorId, targetTenant.getName(), targetTenantId);
    }

    /**
     * 停止代操作模式。
     * <p>
     * 清除当前用户 Sa-Token User-Session 中的代理状态。
     *
     * @param clientIp 客户端 IP
     */
    public void stopProxy(String clientIp) {
        long operatorId = TenantContext.requireUserId();

        if (!TenantContext.isProxyMode()) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "当前未处于代操作模式");
        }

        Long targetTenantId = TenantContext.getProxyTargetTenantId();
        String operatorName = StpUtil.getLoginIdAsString(); // fallback

        // 获取操作人显示名
        try {
            SysUser operator = sysUserMapper.selectById(operatorId);
            if (operator != null) {
                operatorName = operator.getDisplayName() != null
                        ? operator.getDisplayName() : operator.getUsername();
            }
        } catch (Exception e) {
            log.warn("获取操作人名称失败: {}", e.getMessage());
        }

        // 清除 Token-Session 中的代理状态
        SaSession tokenSession = StpUtil.getSession();
        tokenSession.delete(TenantContext.SESSION_KEY_PROXY_TENANT_ID);
        tokenSession.delete(TenantContext.SESSION_KEY_PROXY_OPERATOR_ID);
        tokenSession.delete(TenantContext.SESSION_KEY_PROXY_OPERATOR_NAME);

        // 写入审计日志（fail-close）
        if (targetTenantId != null) {
            writeAuditLogDirect(
                    targetTenantId,
                    TARGET_TYPE_TENANT,
                    String.valueOf(targetTenantId),
                    ACTION_PROXY_STOP,
                    operatorId,
                    operatorName,
                    targetTenantId,
                    1,
                    null,
                    null,
                    RESULT_SUCCESS,
                    "",
                    "退出代操作",
                    clientIp,
                    true  // fail-close
            );
        }

        log.info("超级管理员 {}（ID={}）退出代操作模式", operatorName, operatorId);
    }

    // ==================== 通用审计日志 ====================

    /**
     * 写入审计日志（使用当前上下文自动填充操作人信息）。
     * <p>
     * 供业务模块在写操作完成后调用。自动从 TenantContext 获取操作人、代操作状态等信息。
     *
     * @param tenantId   目标租户 ID（可为 null）
     * @param targetType 目标类型
     * @param targetId   目标业务主键
     * @param action     操作类型
     * @param beforeValue 操作前数据（JSON，可为 null）
     * @param afterValue  操作后数据（JSON，可为 null）
     * @param result     操作结果
     * @param failReason 失败原因
     * @param reason     操作原因
     * @param clientIp   客户端 IP
     */
    public void writeAuditLog(Long tenantId, String targetType, String targetId, String action,
                              String beforeValue, String afterValue,
                              String result, String failReason, String reason, String clientIp) {
        long operatorId = TenantContext.requireUserId();

        String operatorName;
        try {
            SysUser operator = sysUserMapper.selectById(operatorId);
            if (operator != null) {
                operatorName = operator.getDisplayName() != null
                        ? operator.getDisplayName() : operator.getUsername();
            } else {
                operatorName = String.valueOf(operatorId);
            }
        } catch (Exception e) {
            operatorName = String.valueOf(operatorId);
        }

        boolean isProxy = TenantContext.isProxyMode();
        Long proxyTargetTenantId = isProxy ? TenantContext.getProxyTargetTenantId() : null;

        writeAuditLogDirect(
                tenantId, targetType, targetId, action,
                operatorId, operatorName, proxyTargetTenantId,
                isProxy ? 1 : 0,
                beforeValue, afterValue,
                result, failReason, reason, clientIp
        );
    }

    /**
     * 直接写入审计日志（不依赖上下文，由调用方提供所有参数）。
     * <p>
     * <strong>P0 安全修复（FIX-05-R2）</strong>：
     * 代理操作（proxy_start / proxy_stop）的审计日志写入失败时 fail-close，
     * 不允许在不可审计的情况下继续执行代理操作。
     * 其他业务审计日志写入失败仍允许业务继续，但记录 ERROR 日志。
     *
     * @param failCloseOnError true 如果审计写入失败应抛出异常（用于代理操作）
     */
    private void writeAuditLogDirect(Long tenantId, String targetType, String targetId, String action,
                                     Long operatorId, String operatorName, Long targetTenantId, int isProxy,
                                     String beforeValue, String afterValue,
                                     String result, String failReason, String reason, String clientIp) {
        writeAuditLogDirect(tenantId, targetType, targetId, action,
                operatorId, operatorName, targetTenantId, isProxy,
                beforeValue, afterValue, result, failReason, reason, clientIp, false);
    }

    /**
     * 直接写入审计日志（fail-close 控制）。
     *
     * @param failCloseOnError true 如果审计写入失败应抛出异常（用于代理操作）
     */
    private void writeAuditLogDirect(Long tenantId, String targetType, String targetId, String action,
                                     Long operatorId, String operatorName, Long targetTenantId, int isProxy,
                                     String beforeValue, String afterValue,
                                     String result, String failReason, String reason, String clientIp,
                                     boolean failCloseOnError) {
        try {
            SysAuditLog logEntry = new SysAuditLog();
            logEntry.setTenantId(tenantId);
            logEntry.setTargetType(targetType);
            logEntry.setTargetId(targetId != null ? targetId : "");
            logEntry.setAction(action);
            logEntry.setOperatorId(operatorId);
            logEntry.setOperatorName(operatorName != null ? operatorName : "");
            logEntry.setTargetTenantId(targetTenantId);
            logEntry.setIsProxy(isProxy);
            logEntry.setBeforeValue(beforeValue);
            logEntry.setAfterValue(afterValue);
            logEntry.setResult(result != null ? result : RESULT_SUCCESS);
            logEntry.setFailReason(failReason != null ? failReason : "");
            logEntry.setReason(reason != null ? reason : "");
            logEntry.setClientIp(clientIp != null ? clientIp : "");
            logEntry.setCreatedAt(LocalDateTime.now());
            sysAuditLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("审计日志写入失败: action={}, operatorId={}, targetTenantId={}",
                    action, operatorId, targetTenantId, e);
            if (failCloseOnError) {
                throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                        "审计日志写入失败，操作被拒绝以确保安全");
            }
            // 非关键审计日志写入失败不影响主业务
        }
    }

    // ==================== 查询 ====================

    /**
     * 分页查询审计日志。
     * <p>
     * 平台用户可查看全平台日志；租户用户只能查看本租户的日志。
     *
     * @param page    页码
     * @param size    每页大小
     * @param tenantId 租户筛选（可选）
     * @param action   操作类型筛选（可选）
     * @param isProxy  是否代操作筛选（可选）
     * @param operatorId 操作人筛选（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 分页结果
     */
    public IPage<AuditLogVO> listAuditLogs(int page, int size,
                                           Long tenantId, String action, Integer isProxy,
                                           Long operatorId, String startTime, String endTime) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();

        // 数据范围：租户用户只能查看本租户日志
        Long currentTenantId = TenantContext.getTenantId();
        if (currentTenantId != null) {
            wrapper.eq(SysAuditLog::getTenantId, currentTenantId);
        } else if (tenantId != null) {
            // 平台用户可选择筛选目标租户
            wrapper.eq(SysAuditLog::getTenantId, tenantId);
        }

        wrapper.eq(action != null && !action.isBlank(), SysAuditLog::getAction, action)
               .eq(isProxy != null, SysAuditLog::getIsProxy, isProxy)
               .eq(operatorId != null, SysAuditLog::getOperatorId, operatorId);

        // 时间范围
        if (startTime != null && !startTime.isBlank()) {
            try {
                wrapper.ge(SysAuditLog::getCreatedAt, LocalDateTime.parse(startTime));
            } catch (Exception e) {
                // 忽略无效的时间格式
            }
        }
        if (endTime != null && !endTime.isBlank()) {
            try {
                wrapper.le(SysAuditLog::getCreatedAt, LocalDateTime.parse(endTime));
            } catch (Exception e) {
                // 忽略无效的时间格式
            }
        }

        wrapper.orderByDesc(SysAuditLog::getCreatedAt);

        IPage<SysAuditLog> logPage = sysAuditLogMapper.selectPage(new Page<>(page, size), wrapper);
        return logPage.convert(this::toVO);
    }

    /**
     * 查询单条审计日志详情。
     */
    public AuditLogVO getAuditLog(Long id) {
        SysAuditLog logEntry = sysAuditLogMapper.selectById(id);
        if (logEntry == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "审计日志不存在");
        }

        // 数据范围校验
        DataScope.validateTenantMatch(logEntry.getTenantId(), "审计日志");

        return toVO(logEntry);
    }

    /**
     * 获取当前用户代理状态（供 /auth/session 使用）。
     */
    public java.util.Map<String, Object> getProxyStatus() {
        boolean isProxy = TenantContext.isProxyMode();
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("isProxy", isProxy);
        if (isProxy) {
            status.put("proxyOperatorId", TenantContext.getProxyOperatorId());
            status.put("proxyOperatorName", TenantContext.getProxyOperatorName());
            status.put("proxyTargetTenantId", TenantContext.getProxyTargetTenantId());
        }
        return status;
    }

    // ==================== 工具方法 ====================

    private AuditLogVO toVO(SysAuditLog logEntry) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(logEntry.getId());
        vo.setTenantId(logEntry.getTenantId());
        vo.setTargetType(logEntry.getTargetType());
        vo.setTargetId(logEntry.getTargetId());
        vo.setAction(logEntry.getAction());
        vo.setOperatorId(logEntry.getOperatorId());
        vo.setOperatorName(logEntry.getOperatorName());
        vo.setTargetTenantId(logEntry.getTargetTenantId());
        vo.setIsProxy(logEntry.getIsProxy());
        vo.setBeforeValue(logEntry.getBeforeValue());
        vo.setAfterValue(logEntry.getAfterValue());
        vo.setResult(logEntry.getResult());
        vo.setFailReason(logEntry.getFailReason());
        vo.setReason(logEntry.getReason());
        vo.setClientIp(logEntry.getClientIp());
        vo.setCreatedAt(logEntry.getCreatedAt());
        return vo;
    }
}
