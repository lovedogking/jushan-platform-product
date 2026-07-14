package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 访客预约申请创建命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VisitorApplyCreateCmd {

    /** 停车场ID */
    @NotNull(message = "停车场ID不能为空")
    private Long parkingLotId;

    /** 访客姓名 */
    @NotBlank(message = "访客姓名不能为空")
    @Size(max = 30, message = "访客姓名最多30个字符")
    private String visitorName;

    /** 访客电话 */
    @NotBlank(message = "访客电话不能为空")
    @Size(max = 20, message = "访客电话最多20个字符")
    private String visitorPhone;

    /** 访客车牌号 */
    @NotBlank(message = "车牌号不能为空")
    @Pattern(regexp = "^[A-Z0-9]{5,10}$", message = "车牌号格式不正确")
    private String plateNumber;

    /** 来访事由 */
    @Size(max = 200, message = "来访事由最多200个字符")
    private String visitReason;

    /** 被访人姓名 */
    @Size(max = 30, message = "被访人姓名最多30个字符")
    private String hostName;

    /** 被访人电话 */
    @Size(max = 20, message = "被访人电话最多20个字符")
    private String hostPhone;

    /** 被访部门 */
    @Size(max = 50, message = "被访部门最多50个字符")
    private String hostDepartment;

    /** 预约来访日期 */
    @NotNull(message = "预约来访日期不能为空")
    private LocalDate visitDate;

    /** 预约来访开始时间 */
    private LocalTime visitTimeStart;

    /** 预约来访结束时间 */
    private LocalTime visitTimeEnd;
}
