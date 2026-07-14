package com.jushan.platform.modules.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包退款命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class WalletRefundCmd {

    /** 车辆ID */
    @NotNull(message = "车辆ID不能为空")
    private Long vehicleId;

    /** 退款金额（元，必须为正数） */
    @NotNull(message = "退款金额不能为空")
    @Positive(message = "退款金额必须大于0")
    private BigDecimal amount;

    /** 备注 */
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;
}
