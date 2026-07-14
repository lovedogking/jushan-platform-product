package com.jushan.platform.modules.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicleMultiPlate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 一位多车绑定 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysVehicleMultiPlateMapper extends BaseMapper<SysVehicleMultiPlate> {

    /**
     * 查询指定主车辆的绑定车牌列表。
     *
     * @param vehicleId 主车辆ID
     * @return 绑定列表
     */
    @Select("SELECT * FROM sys_vehicle_multi_plate WHERE vehicle_id = #{vehicleId} AND status = 'ACTIVE' AND deleted_at IS NULL")
    List<SysVehicleMultiPlate> selectByVehicleId(@Param("vehicleId") Long vehicleId);

    /**
     * 根据车牌号查询绑定记录。
     *
     * @param plateNumber 车牌号
     * @param tenantId    租户ID
     * @return 绑定列表
     */
    @Select("SELECT * FROM sys_vehicle_multi_plate WHERE plate_number = UPPER(#{plateNumber}) AND tenant_id = #{tenantId} AND status = 'ACTIVE' AND deleted_at IS NULL")
    List<SysVehicleMultiPlate> selectByPlateNumber(@Param("plateNumber") String plateNumber, @Param("tenantId") Long tenantId);

    /**
     * 统计指定主车辆的绑定数量。
     *
     * @param vehicleId 主车辆ID
     * @return 绑定数量
     */
    @Select("SELECT COUNT(*) FROM sys_vehicle_multi_plate WHERE vehicle_id = #{vehicleId} AND status = 'ACTIVE' AND deleted_at IS NULL")
    long countByVehicleId(@Param("vehicleId") Long vehicleId);
}
