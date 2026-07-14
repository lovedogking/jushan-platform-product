package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 费用计算请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeCalculateCmd {

    @NotNull(message = "车场ID不能为空")
    private Long lotId;

    private Long zoneId;

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    @NotBlank(message = "车辆类型不能为空")
    private String vehicleType;

    @NotNull(message = "入场时间不能为空")
    private LocalDateTime entryTime;

    @NotNull(message = "出场时间不能为空")
    private LocalDateTime exitTime;
}
