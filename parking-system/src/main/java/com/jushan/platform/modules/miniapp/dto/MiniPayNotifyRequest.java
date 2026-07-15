package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 小程序支付结果通知请求（前端调用）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MiniPayNotifyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    /** P云支付流水号 */
    @NotBlank(message = "支付流水号不能为空")
    private String paySerial;

    /** 实际支付金额（分） */
    @NotNull(message = "支付金额不能为空")
    private Integer paidAmount;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getPaySerial() { return paySerial; }
    public void setPaySerial(String paySerial) { this.paySerial = paySerial; }

    public Integer getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Integer paidAmount) { this.paidAmount = paidAmount; }
}
