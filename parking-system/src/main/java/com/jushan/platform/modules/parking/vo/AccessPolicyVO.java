package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆进出策略视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class AccessPolicyVO {

    /** 策略ID */
    private Long id;

    /** 停车场ID */
    private Long parkingLotId;

    /** 策略类型 */
    private String policyType;

    /** 策略键 */
    private String policyKey;

    /** 策略值 */
    private String policyValue;

    /** 策略说明 */
    private String description;

    /** 排序 */
    private Integer sortOrder;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
