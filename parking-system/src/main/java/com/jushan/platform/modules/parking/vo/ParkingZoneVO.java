package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区域管理视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingZoneVO {

    private Long id;
    private Long tenantId;
    private Long lotId;
    private String name;
    private String tag;
    private Integer level;
    private Long feeRuleId;
    private Integer totalSpaces;
    private Integer fixedSpaces;
    private Integer tempSpaces;
    private Integer status;
    private Long managerId;
    private String remark;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
