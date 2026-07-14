package com.jushan.platform.infra.log;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务操作日志事件。
 * <p>
 * 由 {@link BusinessLogAspect} 发布，{@link BusinessLogListener} 异步消费并持久化。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BusinessLogEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名/账号 */
    private String operatorName;

    /** 操作IP */
    private String ip;

    /** 操作类型 */
    private String operationType;

    /** 操作对象 */
    private String operationObject;

    /** 对象ID */
    private String objectId;

    /** 变更前值（JSON） */
    private String beforeValue;

    /** 变更后值（JSON） */
    private String afterValue;

    /** 操作结果：1成功 0失败 */
    private Integer result;

    /** 错误信息 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdAt;

    // ==================== 构造器 ====================

    public BusinessLogEvent() {
        this.createdAt = LocalDateTime.now();
        this.result = 1;
    }

    // ==================== Getter / Setter ====================

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperationObject() {
        return operationObject;
    }

    public void setOperationObject(String operationObject) {
        this.operationObject = operationObject;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public Integer getResult() {
        return result;
    }

    public void setResult(Integer result) {
        this.result = result;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
