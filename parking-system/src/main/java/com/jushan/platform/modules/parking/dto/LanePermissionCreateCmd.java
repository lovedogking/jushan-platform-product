package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通道权限创建命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class LanePermissionCreateCmd {

    /** 通道ID */
    @NotNull(message = "通道ID不能为空")
    private Long laneId;

    /** 权限目标类型：VEHICLE-车辆, DEPARTMENT-部门 */
    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "VEHICLE|DEPARTMENT", message = "目标类型必须是 VEHICLE 或 DEPARTMENT")
    private String targetType;

    /** 权限目标ID（车辆ID或部门ID） */
    @NotNull(message = "目标ID不能为空")
    private Long targetId;

    /** 允许方向：ENTRY-仅入口, EXIT-仅出口, BOTH-双向 */
    @NotBlank(message = "方向不能为空")
    @Pattern(regexp = "ENTRY|EXIT|BOTH", message = "方向必须是 ENTRY、EXIT 或 BOTH")
    private String direction;

    /** 有效期开始（可选，NULL表示永久） */
    private LocalDateTime validStart;

    /** 有效期结束（可选，NULL表示永久） */
    private LocalDateTime validEnd;

    /** 备注 */
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;
}
