package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 出场记录实体（P004）。
 * <p>
 * 记录车辆出口识别后的处理结果和放行决策。出场记录与停车记录、
 * 出场识别事件、订单关联，提供完整追溯链路。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>{@code tenant_id} 和 {@code parking_lot_id} 从可信停车记录推导</li>
 *   <li>放行决策仅由后端计算，不信任前端传入</li>
 *   <li>金额使用整数分，禁止负值</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("exit_record")
public class ExitRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（从停车记录推导） */
    private Long tenantId;

    /** 停车场 ID（从停车记录推导） */
    private Long parkingLotId;

    /** 关联停车记录 ID */
    private Long parkingRecordId;

    /** 关联的出场识别事件 ID（recognition_event_log.id） */
    private Long exitEventId;

    /** 出场车道 ID */
    private Long laneId;

    /** 出场相机设备 ID */
    private Long deviceId;

    /** 标准化车牌号 */
    private String standardizedPlate;

    /** 出场时间 */
    private LocalDateTime exitTime;

    /** 计算费用（分） */
    private Integer feeCents;

    /** 已支付金额（分） */
    private Integer paidCents;

    /** 放行决策 */
    private String releaseDecision;

    /** 关联订单 ID */
    private Long orderId;

    /** 决策原因/备注 */
    private String reason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== 常量定义 ====================

    /** 已支付放行 */
    public static final String DECISION_PAID = "PAID";
    /** 零元放行 */
    public static final String DECISION_ZERO_FEE = "ZERO_FEE";
    /** 授权放行（月卡/白名单等，P020 扩展） */
    public static final String DECISION_UNAUTHORIZED = "UNAUTHORIZED";
    /** 待支付，不放行 */
    public static final String DECISION_PENDING_PAYMENT = "PENDING_PAYMENT";
    /** 无在场记录 */
    public static final String DECISION_NO_RECORD = "NO_RECORD";
    /** 异常 */
    public static final String DECISION_EXCEPTION = "EXCEPTION";
    /** 欠费放行：车场策略 ALLOW_ARREARS，开闸放行并记录欠费 */
    public static final String DECISION_ARREARS_ALLOWED = "ARREARS_ALLOWED";
    /** 欠费提醒放行：REMIND_ONLY 策略，放行并推送提醒 */
    public static final String DECISION_ARREARS_REMIND = "ARREARS_REMIND";
    /** 欠费合并计费：MUST_PAY 策略，拦截并提示补缴 */
    public static final String DECISION_ARREARS_MUST_PAY = "ARREARS_MUST_PAY";

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getParkingRecordId() { return parkingRecordId; }
    public void setParkingRecordId(Long parkingRecordId) { this.parkingRecordId = parkingRecordId; }

    public Long getExitEventId() { return exitEventId; }
    public void setExitEventId(Long exitEventId) { this.exitEventId = exitEventId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getStandardizedPlate() { return standardizedPlate; }
    public void setStandardizedPlate(String standardizedPlate) { this.standardizedPlate = standardizedPlate; }

    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }

    public Integer getFeeCents() { return feeCents; }
    public void setFeeCents(Integer feeCents) { this.feeCents = feeCents; }

    public Integer getPaidCents() { return paidCents; }
    public void setPaidCents(Integer paidCents) { this.paidCents = paidCents; }

    public String getReleaseDecision() { return releaseDecision; }
    public void setReleaseDecision(String releaseDecision) { this.releaseDecision = releaseDecision; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 逻辑删除时间（NULL 表示未删除） */
    private LocalDateTime deletedAt;

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
