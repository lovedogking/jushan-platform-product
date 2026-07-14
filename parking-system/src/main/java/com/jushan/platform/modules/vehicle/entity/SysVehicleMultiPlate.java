package com.jushan.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 一位多车绑定实体。
 * <p>
 * 一个车主可绑定多个车牌，默认上限由车场配置控制。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_vehicle_multi_plate")
public class SysVehicleMultiPlate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主车辆ID */
    private Long vehicleId;

    /** 绑定车牌号（大写） */
    private String plateNumber;

    /** 绑定状态：ACTIVE-有效, DISABLED-已禁用 */
    private String status;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
}
