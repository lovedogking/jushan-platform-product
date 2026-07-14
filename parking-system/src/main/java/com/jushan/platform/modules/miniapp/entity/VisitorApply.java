package com.jushan.platform.modules.miniapp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 访客预约申请实体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("visitor_apply")
public class VisitorApply extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 停车场ID */
    private Long parkingLotId;

    /** 访客姓名 */
    private String visitorName;

    /** 访客电话 */
    private String visitorPhone;

    /** 访客车牌号（大写） */
    private String plateNumber;

    /** 来访事由 */
    private String visitReason;

    /** 被访人姓名 */
    private String hostName;

    /** 被访人电话 */
    private String hostPhone;

    /** 被访部门 */
    private String hostDepartment;

    /** 预约来访日期 */
    private LocalDate visitDate;

    /** 预约来访开始时间 */
    private LocalTime visitTimeStart;

    /** 预约来访结束时间 */
    private LocalTime visitTimeEnd;

    /** 申请状态：PENDING-待审核, APPROVED-已通过, REJECTED-已拒绝, CANCELLED-已取消 */
    private String applyStatus;

    /** 审核结果说明 */
    private String auditResult;

    /** 审核人ID */
    private Long auditorId;

    /** 审核时间 */
    private java.time.LocalDateTime auditedAt;

    /** 申请人ID（小程序用户） */
    private Long applicantId;

    // ==================== 常量定义 ====================

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
}
