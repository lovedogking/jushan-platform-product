package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆审核记录视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleAuditVO {

    /** 审核ID */
    private Long id;

    /** 车辆ID */
    private Long vehicleId;

    /** 车牌号 */
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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
