package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车场档案视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingLotVO {

    private Long id;
    private Long tenantId;
    private Long companyId;
    private Long groupId;
    private String name;
    private String province;
    private String city;
    private String district;
    private Integer regionType;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private String businessHours;
    private Integer totalSpaces;
    private String images;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
