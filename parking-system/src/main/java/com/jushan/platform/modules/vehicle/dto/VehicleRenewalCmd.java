package com.jushan.platform.modules.vehicle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 月卡/固定车续费命令。
 * <p>
 * 由管理员在运营平台发起。续费金额（分）由前端根据收费标准/月租金计算后传入；
 * 后端不再二次信任前端金额作为唯一依据，而是原样落入订单并要求支付回调对账。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleRenewalCmd {

    /** 续费月数（≥1） */
    @NotNull(message = "续费月数不能为空")
    @Min(value = 1, message = "续费月数必须大于0")
    private Integer renewalMonths;

    /** 支付方式：PYUN-P云, WECHAT-微信, ALIPAY-支付宝, CASH-现金, BALANCE-余额 */
    @NotBlank(message = "支付方式不能为空")
    private String payChannel;

    /** 续费金额（分），≥0；0 表示免费续费 */
    @NotNull(message = "续费金额不能为空")
    @Min(value = 0, message = "续费金额不能为负")
    private Integer amountCents;
}
