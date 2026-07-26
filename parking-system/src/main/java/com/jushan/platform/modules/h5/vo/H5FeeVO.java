package com.jushan.platform.modules.h5.vo;

import java.io.Serializable;

/**
 * H5 查费结果 VO（单条在场记录）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class H5FeeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID（复用已有 PENDING_PAY 订单或新建的订单） */
    private Long orderId;

    /** 停车记录 ID */
    private Long recordId;

    /** 停车场 ID */
    private Long parkingLotId;

    /** 车场名称 */
    private String parkName;

    /** 标准化车牌号 */
    private String plate;

    /** 入场时间 */
    private String entryTime;

    /** 停车时长（分钟） */
    private Long durationMinutes;

    /** 费用（分） */
    private Integer feeCents;

    /** 费用（元），2 位小数 */
    private String feeYuan;

    /** 是否可支付 */
    private Boolean payable;

    // ==================== getter / setter ====================

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkName() { return parkName; }
    public void setParkName(String parkName) { this.parkName = parkName; }

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }

    public String getEntryTime() { return entryTime; }
    public void setEntryTime(String entryTime) { this.entryTime = entryTime; }

    public Long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Long durationMinutes) { this.durationMinutes = durationMinutes; }

    public Integer getFeeCents() { return feeCents; }
    public void setFeeCents(Integer feeCents) { this.feeCents = feeCents; }

    public String getFeeYuan() { return feeYuan; }
    public void setFeeYuan(String feeYuan) { this.feeYuan = feeYuan; }

    public Boolean getPayable() { return payable; }
    public void setPayable(Boolean payable) { this.payable = payable; }
}
