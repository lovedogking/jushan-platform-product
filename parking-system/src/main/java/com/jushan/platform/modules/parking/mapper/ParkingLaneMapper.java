package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingLane;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通道管理 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
@Repository("modulesParkingLaneMapper")
public interface ParkingLaneMapper extends BaseMapper<ParkingLane> {

    /**
     * 按 ID 查询，忽略租户拦截器。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_lane WHERE id = #{id}")
    ParkingLane selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 按车场 ID 和区域 ID 查询通道列表。
     *
     * @param lotId  车场 ID
     * @param zoneId 区域 ID（可选）
     * @return 通道列表
     */
    List<ParkingLane> selectListByLotIdAndZoneId(@Param("lotId") Long lotId, @Param("zoneId") Long zoneId);

    /**
     * 按通道编号查询（用于唯一性校验）。
     */
    @Select("SELECT * FROM parking_lane WHERE tenant_id = #{tenantId} AND lot_id = #{lotId} AND lane_no = #{laneNo} AND deleted_at IS NULL LIMIT 1")
    ParkingLane selectByLaneNo(@Param("tenantId") Long tenantId, @Param("lotId") Long lotId, @Param("laneNo") String laneNo);
}
