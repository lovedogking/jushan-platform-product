package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 车道 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingLaneMapper extends BaseMapper<ParkingLane> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_lane WHERE id = #{id}")
    ParkingLane selectByIdIgnoreTenant(@Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_lane WHERE lot_id = #{lotId} AND deleted_at IS NULL ORDER BY id ASC")
    List<ParkingLane> selectByLotIdIgnoreTenant(@Param("lotId") Long lotId);
}
