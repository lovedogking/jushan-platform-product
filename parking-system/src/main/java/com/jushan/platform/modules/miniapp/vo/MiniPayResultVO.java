package com.jushan.platform.modules.miniapp.vo;

import java.io.Serializable;
import java.util.Map;

/**
 * 小程序支付预下单结果 VO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MiniPayResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** P云支付流水号 */
    private String paySerial;

    /** 支付渠道 */
    private String payChannel;

    /** 支付状态 */
    private String status;

    /** 预支付参数（传递给 wx.requestPayment） */
    private Map<String, Object> prepayParams;

    /** 应付金额（分） */
    private Integer payableAmount;

    /** 应付金额（元），2 位小数 */
    private String payableAmountYuan;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getPaySerial() { return paySerial; }
    public void setPaySerial(String paySerial) { this.paySerial = paySerial; }

    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> getPrepayParams() { return prepayParams; }
    public void setPrepayParams(Map<String, Object> prepayParams) { this.prepayParams = prepayParams; }

    public Integer getPayableAmount() { return payableAmount; }
    public void setPayableAmount(Integer payableAmount) { this.payableAmount = payableAmount; }

    public String getPayableAmountYuan() { return payableAmountYuan; }
    public void setPayableAmountYuan(String payableAmountYuan) { this.payableAmountYuan = payableAmountYuan; }
}
