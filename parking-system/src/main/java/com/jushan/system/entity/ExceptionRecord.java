package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异常记录实体（Phase 2 D2）。
 * <p>
 * 记录系统运行中的异常事件，包括重复入场、识别失败、黑名单告警、未支付拦截。
 * 与 MonitorAlert 的差异：MonitorAlert 用于岗亭端实时推送，粒度更细；
 * ExceptionRecord 用于运营管理端查看和追踪，包含运营侧所需完整信息。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>{@code tenant_id} 从可信停车场记录推导</li>
 *   <li>数据处理状态变更使用条件更新</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("exception_record")
public class ExceptionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 异常类型常量 ====================

    /** 重复入场 */
    public static final String TYPE_DUP_ENTRY = "DUP_ENTRY";

    /** 识别失败 */
    public static final String TYPE_RECOGNITION_FAIL = "RECOGNITION_FAIL";

    /** 黑名单告警 */
    public static final String TYPE_BLACKLIST = "BLACKLIST";

    /** 未支付拦截 */
    public static final String TYPE_UNPAID_INTERCEPT = "UNPAID_INTERCEPT";

    // ==================== 处理状态常量 ====================

    /** 未处理 */
    public static final String STATUS_UNHANDLED = "UNHANDLED";

    /** 已处理 */
    public static final String STATUS_HANDLED = "HANDLED";

    // ==================== 字段 ====================

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 通道 ID */
    private Long laneId;

    /** 异常类型：DUP_ENTRY-重复入场, RECOGNITION_FAIL-识别失败, BLACKLIST-黑名单告警, UNPAID_INTERCEPT-未支付拦截 */
    private String exceptionType;

    /** 车牌号 */
    private String plateNumber;

    /** 处理状态：UNHANDLED-未处理, HANDLED-已处理 */
    private String status;

    /** 异常描述 */
    private String description;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理人（sys_user.id） */
    private Long handler;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除时间 */
    private LocalDateTime deletedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }

    public Long getHandler() { return handler; }
    public void setHandler(Long handler) { this.handler = handler; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
