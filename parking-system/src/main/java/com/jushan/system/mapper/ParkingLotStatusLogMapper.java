package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.ParkingLotStatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 停车场状态变更审计日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingLotStatusLogMapper extends BaseMapper<ParkingLotStatusLog> {
}
