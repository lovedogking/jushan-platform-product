package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通道权限更新命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class LanePermissionUpdateCmd {

    /** 允许方向：ENTRY-仅入口, EXIT-仅出口, BOTH-双向 */
    @NotBlank(message = "方向不能为空")
    @Pattern(regexp = "ENTRY|EXIT|BOTH", message = "方向必须是 ENTRY、EXIT 或 BOTH")
    private String direction;

    /** 有效期开始（可选，NULL表示永久） */
    private LocalDateTime validStart;

    /** 有效期结束（可选，NULL表示永久） */
    private LocalDateTime validEnd;

    /** 状态：ACTIVE-生效, DISABLED-已禁用 */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "状态必须是 ACTIVE 或 DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;
}
