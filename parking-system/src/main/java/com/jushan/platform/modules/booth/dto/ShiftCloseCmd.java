package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 交接班交班命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ShiftCloseCmd {

    /** 交接班记录ID */
    @NotNull(message = "记录ID不能为空")
    private Long shiftId;

    /** 接班人ID */
    private Long handoverTo;

    /** 交接备注 */
    @Size(max = 200, message = "交接备注最多200个字符")
    private String handoverRemark;

    /** 操作员确认实收金额（元），不传则等于系统应收 */
    private BigDecimal confirmedCashAmount;

    /** 实收金额校正原因（与系统应收不一致时必填） */
    private String adjustReason;
}
