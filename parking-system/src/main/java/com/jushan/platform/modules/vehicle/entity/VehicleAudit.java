package com.jushan.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 车辆审核记录实体。
 * <p>
 * 支持车辆登记审核流程：待审核、已通过、已驳回、待补充。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vehicle_audit")
public class VehicleAudit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 车辆ID */
    private Long vehicleId;

    /** 车牌号（大写） */
    private String plateNumber;

    /** 申请类型：NEW-新登记, UPDATE-信息变更, RENEW-续期 */
    private String applyType;

    /** 申请原因/备注 */
    private String applyReason;

    /** 申请人ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 申请人电话 */
    private String applicantPhone;

    /** 审核状态：PENDING-待审核, APPROVED-已通过, REJECTED-已驳回, NEED_INFO-待补充 */
    private String auditStatus;

    /** 审核结果说明 */
    private String auditResult;

    /** 审核人ID */
    private Long auditorId;

    /** 审核人姓名 */
    private String auditorName;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    // ==================== 常量定义 ====================

    public static final String APPLY_NEW = "NEW";
    public static final String APPLY_UPDATE = "UPDATE";
    public static final String APPLY_RENEW = "RENEW";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_NEED_INFO = "NEED_INFO";
}
