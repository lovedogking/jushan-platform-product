package com.jushan.platform.modules.miniapp.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 访客预约申请视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VisitorApplyVO {

    /** 预约ID */
    private Long id;

    /** 停车场ID */
    private Long parkingLotId;

    /** 访客姓名 */
    private String visitorName;

    /** 访客电话 */
    private String visitorPhone;

    /** 访客车牌号 */
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

    /** 申请状态 */
    private String applyStatus;

    /** 审核结果说明 */
    private String auditResult;

    /** 审核人ID */
    private Long auditorId;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 申请人ID */
    private Long applicantId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
