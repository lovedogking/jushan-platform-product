package com.jushan.platform.modules.parking.vo;

import lombok.Data;

/**
 * 车位余位信息视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingSpaceRemainVO {

    /** 停车场ID */
    private Long parkingLotId;

    /** 区域ID（NULL表示全场） */
    private Long zoneId;

    /** 总车位数 */
    private Integer totalSpaces;

    /** 已用车位数 */
    private Integer usedSpaces;

    /** 剩余车位数 */
    private Integer remainSpaces;

    /** 是否预警（剩余 <= 预警阈值） */
    private Boolean warning;

    /** 是否已满 */
    private Boolean full;
}
