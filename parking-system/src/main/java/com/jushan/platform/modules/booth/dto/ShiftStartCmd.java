package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 交接班开班命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ShiftStartCmd {

    /** 停车场ID */
    @NotNull(message = "停车场ID不能为空")
    private Long parkingLotId;

    /** 班次类型：MORNING-早班, AFTERNOON-中班, NIGHT-晚班 */
    @NotBlank(message = "班次类型不能为空")
    @Pattern(regexp = "MORNING|AFTERNOON|NIGHT", message = "班次类型必须是 MORNING、AFTERNOON 或 NIGHT")
    private String shiftType;

    /** 操作员姓名 */
    @Size(max = 30, message = "操作员姓名最多30个字符")
    private String operatorName;
}
