package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingRecordSyncLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * P云停车记录同步日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingRecordSyncLogMapper extends BaseMapper<ParkingRecordSyncLog> {
}
