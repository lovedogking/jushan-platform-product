package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingLotCapacityLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 停车场容量变更审计日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingLotCapacityLogMapper extends BaseMapper<ParkingLotCapacityLog> {
}
