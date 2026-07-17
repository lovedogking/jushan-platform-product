package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志实体（任务包 1-2）。
 * <p>
 * 记录每一次订单状态迁移：订单号、源状态、目标状态、触发源、操作人、时间。
 * 追加写入，供订单详情追溯状态流转历史。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("order_status_log")
public class OrderStatusLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（从订单推导） */
    private Long tenantId;

    /** 停车场 ID（从订单推导） */
    private Long parkingLotId;

    /** 关联停车订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 源状态（创建时为空） */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 触发源：SYSTEM/USER/BOOTH/TIMER */
    private String triggerSource;

    /** 操作人 ID（系统/定时任务为空） */
    private Long operatorId;

    /** 操作人名称/标记 */
    private String operatorName;

    /** 备注（如退款原因） */
    private String remark;

    /** 流转时间 */
    private LocalDateTime createdAt;

    // ==================== 触发源常量 ====================

    /** 系统触发（识别事件/入场/出场自动流转） */
    public static final String TRIGGER_SYSTEM = "SYSTEM";
    /** 用户触发（小程序/运营端人工操作） */
    public static final String TRIGGER_USER = "USER";
    /** 岗亭触发 */
    public static final String TRIGGER_BOOTH = "BOOTH";
    /** 定时任务触发（超时关闭等） */
    public static final String TRIGGER_TIMER = "TIMER";

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
