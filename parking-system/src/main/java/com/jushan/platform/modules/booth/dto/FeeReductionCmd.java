package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 费用减免命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeReductionCmd {

    /** 在场记录ID */
    @NotNull(message = "在场记录ID不能为空")
    private Long sessionId;

    /** 原应收金额（分） */
    @NotNull(message = "原应收金额不能为空")
    @Min(value = 0, message = "原应收金额不能为负数")
    private Integer originalFeeCents;

    /** 减免后应收金额（分） */
    @NotNull(message = "减免后金额不能为空")
    @Min(value = 0, message = "减免后金额不能为负数")
    private Integer reducedFeeCents;

    /** 减免金额（分） */
    @NotNull(message = "减免金额不能为空")
    @Min(value = 0, message = "减免金额不能为负数")
    private Integer reductionCents;

    /** 减免原因 */
    @NotBlank(message = "减免原因不能为空")
    @Size(max = 200, message = "减免原因最多200个字符")
    private String reason;
}
