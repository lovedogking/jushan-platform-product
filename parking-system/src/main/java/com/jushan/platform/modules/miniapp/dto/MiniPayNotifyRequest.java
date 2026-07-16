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

    /** 支付流水号（模拟支付模式留空，由后端自动生成） */
    private String paySerial;

    /** 实际支付金额（模拟支付模式留空，从订单读取） */
    private Integer paidAmount;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getPaySerial() { return paySerial; }
    public void setPaySerial(String paySerial) { this.paySerial = paySerial; }

    public Integer getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Integer paidAmount) { this.paidAmount = paidAmount; }
}
