package com.jushan.platform.modules.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.vehicle.entity.Vehicle;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车辆 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface VehicleMapper extends BaseMapper<Vehicle> {
}