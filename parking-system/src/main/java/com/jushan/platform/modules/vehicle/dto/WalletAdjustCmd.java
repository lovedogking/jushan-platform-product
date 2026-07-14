package com.jushan.platform.modules.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包调账命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class WalletAdjustCmd {

    /** 车辆ID */
    @NotNull(message = "车辆ID不能为空")
    private Long vehicleId;

    /** 调账金额（元，正数增加余额，负数减少余额） */
    @NotNull(message = "调账金额不能为空")
    private BigDecimal amount;

    /** 备注 */
    @NotNull(message = "调账备注不能为空")
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;
}
