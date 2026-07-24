package com.jushan.platform.modules.parking.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志视图 VO（任务包 1-2）。
 * <p>
 * 用于运营端订单详情展示状态流转历史。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class OrderStatusLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源状态 */
    private String fromStatus;

    /** 源状态中文 */
    private String fromStatusLabel;

    /** 目标状态 */
    private String toStatus;

    /** 目标状态中文 */
    private String toStatusLabel;

    /** 触发源：SYSTEM/USER/BOOTH/TIMER */
    private String triggerSource;

    /** 触发源中文 */
    private String triggerSourceLabel;

    /** 操作人 */
    private String operatorName;

    /** 备注（如退款原因） */
    private String remark;

    /** 流转时间 */
    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getFromStatusLabel() { return fromStatusLabel; }
    public void setFromStatusLabel(String fromStatusLabel) { this.fromStatusLabel = fromStatusLabel; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getToStatusLabel() { return toStatusLabel; }
    public void setToStatusLabel(String toStatusLabel) { this.toStatusLabel = toStatusLabel; }

    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }

    public String getTriggerSourceLabel() { return triggerSourceLabel; }
    public void setTriggerSourceLabel(String triggerSourceLabel) { this.triggerSourceLabel = triggerSourceLabel; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
