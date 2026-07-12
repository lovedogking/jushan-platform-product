package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 高风险操作审计日志实体。
 * <p>
 * 记录超级管理员代操作、人工开闸、车牌修正、人工减免等高风险操作的完整审计信息。
 * 记录真实操作人、目标租户/停车场、操作前后值和操作结果。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标租户 ID（操作发生时有效的 tenant_id） */
    private Long tenantId;

    /** 目标类型（如 parking_lot, fee_rule, order 等） */
    private String targetType;

    /** 目标业务主键（如停车场 ID、订单号等） */
    private String targetId;

    /** 操作类型（如 proxy_start, proxy_stop, gate_open, fee_adjust 等） */
    private String action;

    /** 真实操作人 ID（sys_user.id，总是实际登录用户） */
    private Long operatorId;

    /** 真实操作人登录账号/显示名 */
    private String operatorName;

    /** 代操作目标租户 ID（仅在代理模式下有效） */
    private Long targetTenantId;

    /** 是否为代操作：1-是，0-否 */
    private Integer isProxy;

    /** 操作前数据（JSON 格式，可选） */
    private String beforeValue;

    /** 操作后数据（JSON 格式，可选） */
    private String afterValue;

    /** 操作结果：SUCCESS, FAILED, UNCERTAIN */
    private String result;

    /** 失败原因（result=FAILED 时填写） */
    private String failReason;

    /** 操作原因/备注（由操作人填写） */
    private String reason;

    /** 操作客户端 IP */
    private String clientIp;

    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public Long getTargetTenantId() { return targetTenantId; }
    public void setTargetTenantId(Long targetTenantId) { this.targetTenantId = targetTenantId; }

    public Integer getIsProxy() { return isProxy != null ? isProxy : 0; }
    public void setIsProxy(Integer isProxy) { this.isProxy = isProxy; }

    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(String beforeValue) { this.beforeValue = beforeValue; }

    public String getAfterValue() { return afterValue; }
    public void setAfterValue(String afterValue) { this.afterValue = afterValue; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
