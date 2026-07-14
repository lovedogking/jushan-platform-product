package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车位管控策略视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingSpacePolicyVO {

    /** 策略ID */
    private Long id;

    /** 停车场ID */
    private Long parkingLotId;

    /** 区域ID */
    private Long zoneId;

    /** 总车位数 */
    private Integer totalSpaces;

    /** 固定车位数 */
    private Integer fixedSpaces;

    /** 临时车位数 */
    private Integer tempSpaces;

    /** 预留车位数 */
    private Integer reservedSpaces;

    /** 余位预警阈值 */
    private Integer warningThreshold;

    /** 满位动作 */
    private String fullAction;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
