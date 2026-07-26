package com.jushan.platform.modules.h5.vo;

import java.io.Serializable;

/**
 * H5 支付结果 VO（prepare / query 共用）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class H5PayResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 车牌号 */
    private String plate;

    /** 车场名称 */
    private String parkName;

    /** 应付金额（分） */
    private Integer payableAmount;

    /** 应付金额（元），2 位小数 */
    private String payableAmountYuan;

    /** 订单状态：PENDING_PAY / PAYING / PAID / FAILED / CANCELLED */
    private String status;

    /** 是否模拟支付模式 */
    private Boolean mock;

    /** 实付金额（分），仅 query 返回 */
    private Integer paidAmount;

    /** 支付时间，仅 query 返回 */
    private String paidTime;

    // ==================== getter / setter ====================

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }

    public String getParkName() { return parkName; }
    public void setParkName(String parkName) { this.parkName = parkName; }

    public Integer getPayableAmount() { return payableAmount; }
    public void setPayableAmount(Integer payableAmount) { this.payableAmount = payableAmount; }

    public String getPayableAmountYuan() { return payableAmountYuan; }
    public void setPayableAmountYuan(String payableAmountYuan) { this.payableAmountYuan = payableAmountYuan; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getMock() { return mock; }
    public void setMock(Boolean mock) { this.mock = mock; }

    public Integer getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Integer paidAmount) { this.paidAmount = paidAmount; }

    public String getPaidTime() { return paidTime; }
    public void setPaidTime(String paidTime) { this.paidTime = paidTime; }
}
