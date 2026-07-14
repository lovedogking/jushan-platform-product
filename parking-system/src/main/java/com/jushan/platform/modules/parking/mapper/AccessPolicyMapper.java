package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.AccessPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 车辆进出策略配置 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface AccessPolicyMapper extends BaseMapper<AccessPolicy> {

    /**
     * 查询指定停车场的所有策略。
     *
     * @param parkingLotId 停车场ID
     * @param tenantId     租户ID
     * @return 策略列表
     */
    @Select("SELECT * FROM access_policy WHERE parking_lot_id = #{parkingLotId} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE' ORDER BY sort_order")
    List<AccessPolicy> selectByParkingLotId(@Param("parkingLotId") Long parkingLotId, @Param("tenantId") Long tenantId);

    /**
     * 查询指定停车场的指定类型策略。
     *
     * @param parkingLotId 停车场ID
     * @param policyType   策略类型
     * @param tenantId     租户ID
     * @return 策略列表
     */
    @Select("SELECT * FROM access_policy WHERE parking_lot_id = #{parkingLotId} AND policy_type = #{policyType} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE' ORDER BY sort_order")
    List<AccessPolicy> selectByType(@Param("parkingLotId") Long parkingLotId, @Param("policyType") String policyType, @Param("tenantId") Long tenantId);
}
