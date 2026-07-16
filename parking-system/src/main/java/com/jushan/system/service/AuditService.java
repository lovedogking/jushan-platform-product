package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.entity.SysAuditLog;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.SysAuditLogMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.mybatis.TenantIgnore;
import com.jushan.system.vo.AuditLogVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计服务。
 *
 * 负责通用高风险操作审计日志写入和查询。
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

    /** 目标类型 */
    public static final String TARGET_TYPE_TENANT = "tenant";

    private final SysUserMapper sysUserMapper;
    private final SysAuditLogMapper sysAuditLogMapper;

    public AuditService(SysUserMapper sysUserMapper,
                       SysAuditLogMapper sysAuditLogMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysAuditLogMapper = sysAuditLogMapper;
    }

    // ==================== 通用审计日志 ====================

    /**
     * 写入审计日志（使用当前上下文自动填充操作人信息）。
     * <p>
     * 供业务模块在写操作完成后调用。自动从 TenantContext 获取操作人等信息。
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

        writeAuditLogDirect(
                tenantId, targetType, targetId, action,
                operatorId, operatorName, null,
                0,
                beforeValue, afterValue,
                result, failReason, reason, clientIp
        );
    }

    /**
     * 写入审计日志（显式提供操作人，不依赖当前线程上下文）。
     * <p>
     * 供无用户上下文的场景使用，例如支付回调（P云异步通知）中触发的续费生效。
     * 若未提供 operatorName，则按 operatorId 反查操作员姓名。
     *
     * @param tenantId     目标租户 ID（可为 null）
     * @param targetType   目标类型
     * @param targetId     目标业务主键
     * @param action       操作类型
     * @param operatorId   操作人 ID（可为 null，例如系统触发）
     * @param operatorName 操作人名称（可为 null，将按 operatorId 反查）
     * @param beforeValue  操作前数据（JSON，可为 null）
     * @param afterValue   操作后数据（JSON，可为 null）
     * @param result       操作结果
     * @param failReason   失败原因
     * @param reason       操作原因
     * @param clientIp     客户端 IP
     */
    public void writeAuditLog(Long tenantId, String targetType, String targetId, String action,
                              Long operatorId, String operatorName,
                              String beforeValue, String afterValue,
                              String result, String failReason, String reason, String clientIp) {
        String resolvedName = operatorName;
        if (resolvedName == null && operatorId != null) {
            try {
                SysUser operator = sysUserMapper.selectById(operatorId);
                if (operator != null) {
                    resolvedName = operator.getDisplayName() != null
                            ? operator.getDisplayName() : operator.getUsername();
                } else {
                    resolvedName = String.valueOf(operatorId);
                }
            } catch (Exception e) {
                resolvedName = String.valueOf(operatorId);
            }
        } else if (resolvedName == null) {
            resolvedName = "";
        }

        writeAuditLogDirect(
                tenantId, targetType, targetId, action,
                operatorId, resolvedName, null,
                0,
                beforeValue, afterValue,
                result, failReason, reason, clientIp
        );
    }

    /**
     * 直接写入审计日志（不依赖上下文，由调用方提供所有参数）。
     *
     * @param targetTenantId 目标租户 ID（保留字段，当前未使用）
     * @param isProxy        是否代操作（保留字段，当前未使用）
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

    // 代操作审计方法已移除（代操作功能已废弃）

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
    @TenantIgnore(reason = "平台总后台查询全平台审计日志")
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
        SysAuditLog logEntry = sysAuditLogMapper.selectByIdIgnoreTenant(id);
        if (logEntry == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "审计日志不存在");
        }

        // 数据范围校验
        DataScope.validateTenantMatch(logEntry.getTenantId(), "审计日志");

        return toVO(logEntry);
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
