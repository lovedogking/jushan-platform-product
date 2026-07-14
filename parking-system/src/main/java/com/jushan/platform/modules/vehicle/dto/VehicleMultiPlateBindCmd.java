package com.jushan.platform.modules.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 一位多车绑定命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleMultiPlateBindCmd {

    /** 绑定车牌号 */
    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;
}
