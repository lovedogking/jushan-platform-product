package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 车位管控策略创建命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingSpacePolicyCreateCmd {

    /** 停车场ID */
    @NotNull(message = "停车场ID不能为空")
    private Long parkingLotId;

    /** 区域ID（NULL表示全场策略） */
    private Long zoneId;

    /** 总车位数 */
    @NotNull(message = "总车位数不能为空")
    private Integer totalSpaces;

    /** 固定车位数 */
    @NotNull(message = "固定车位数不能为空")
    private Integer fixedSpaces;

    /** 临时车位数 */
    @NotNull(message = "临时车位数不能为空")
    private Integer tempSpaces;

    /** 预留车位数 */
    @NotNull(message = "预留车位数不能为空")
    private Integer reservedSpaces;

    /** 余位预警阈值 */
    private Integer warningThreshold = 10;

    /** 满位动作：WARN-仅预警, BLOCK-禁止入场, ALLOW_VIP-仅允许VIP/月租 */
    @NotBlank(message = "满位动作不能为空")
    @Pattern(regexp = "WARN|BLOCK|ALLOW_VIP", message = "满位动作必须是 WARN、BLOCK 或 ALLOW_VIP")
    private String fullAction;
}
