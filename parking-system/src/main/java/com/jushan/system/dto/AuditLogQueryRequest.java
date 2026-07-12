package com.jushan.system.dto;

/**
 * 审计日志查询请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class AuditLogQueryRequest {

    /** 目标租户 ID（筛选） */
    private Long tenantId;

    /** 操作类型（筛选） */
    private String action;

    /** 是否代操作（筛选） */
    private Integer isProxy;

    /** 操作人 ID（筛选） */
    private Long operatorId;

    /** 开始时间（ISO 格式） */
    private String startTime;

    /** 结束时间（ISO 格式） */
    private String endTime;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getIsProxy() { return isProxy; }
    public void setIsProxy(Integer isProxy) { this.isProxy = isProxy; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
