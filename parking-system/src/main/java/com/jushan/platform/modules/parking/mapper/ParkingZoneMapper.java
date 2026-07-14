package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingZone;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 区域管理 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingZoneMapper extends BaseMapper<ParkingZone> {

    /**
     * 按车场 ID 查询区域列表。
     *
     * @param lotId 车场 ID
     * @return 区域列表
     */
    List<ParkingZone> selectListByLotId(@Param("lotId") Long lotId);
}
