package com.jushan.platform.modules.h5.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * H5 支付通知请求（模拟支付确认）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class H5PayNotifyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    /** 支付动作：success=支付成功, fail=支付失败 */
    @NotNull(message = "action 不能为空")
    private String action;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
