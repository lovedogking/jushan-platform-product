package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 停车场更新请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingLotUpdateCmd {

    private Long companyId;

    private Long groupId;

    @Size(max = 128, message = "名称长度不能超过128")
    private String name;

    private Integer regionType;

    @Size(max = 64, message = "省份长度不能超过64")
    private String province;

    @Size(max = 64, message = "城市长度不能超过64")
    private String city;

    @Size(max = 64, message = "区县长度不能超过64")
    private String district;

    @Size(max = 256, message = "地址长度不能超过256")
    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    @Size(max = 64, message = "联系人长度不能超过64")
    private String contactName;

    @Size(max = 32, message = "联系电话长度不能超过32")
    private String contactPhone;

    private Integer status;

    @Size(max = 32, message = "营业时间长度不能超过32")
    private String businessHours;
}
