package com.jushan.platform.modules.h5.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * H5 支付预下单请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class H5PayPrepareRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
