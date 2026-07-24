package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 停车场容量变更审计日志。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("parking_lot_capacity_log")
public class ParkingLotCapacityLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 变更字段名（total_spaces / remaining_spaces） */
    private String fieldName;

    /** 修改前数值 */
    private Integer beforeValue;

    /** 修改后数值 */
    private Integer afterValue;

    /** 操作人 ID */
    private Long operatorId;

    /** 修改原因 */
    private String reason;

    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public Integer getBeforeValue() { return beforeValue; }
    public void setBeforeValue(Integer beforeValue) { this.beforeValue = beforeValue; }

    public Integer getAfterValue() { return afterValue; }
    public void setAfterValue(Integer afterValue) { this.afterValue = afterValue; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
