package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingSpacePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 车位管控策略 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingSpacePolicyMapper extends BaseMapper<ParkingSpacePolicy> {

    /**
     * 查询指定停车场的车位管控策略。
     *
     * @param parkingLotId 停车场ID
     * @param tenantId     租户ID
     * @return 策略列表
     */
    @Select("SELECT * FROM parking_space_policy WHERE parking_lot_id = #{parkingLotId} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE' ORDER BY zone_id")
    List<ParkingSpacePolicy> selectByParkingLotId(@Param("parkingLotId") Long parkingLotId, @Param("tenantId") Long tenantId);

    /**
     * 查询指定区域的车位管控策略。
     *
     * @param zoneId   区域ID
     * @param tenantId 租户ID
     * @return 策略实体
     */
    @Select("SELECT * FROM parking_space_policy WHERE zone_id = #{zoneId} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE' LIMIT 1")
    ParkingSpacePolicy selectByZoneId(@Param("zoneId") Long zoneId, @Param("tenantId") Long tenantId);
}
