package com.jushan.platform.modules.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.vehicle.entity.VehicleAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 车辆审核记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface VehicleAuditMapper extends BaseMapper<VehicleAudit> {

    /**
     * 查询指定车辆的审核记录。
     *
     * @param vehicleId 车辆ID
     * @param tenantId  租户ID
     * @return 审核记录列表
     */
    @Select("SELECT * FROM vehicle_audit WHERE vehicle_id = #{vehicleId} AND tenant_id = #{tenantId} AND deleted_at IS NULL ORDER BY created_at DESC")
    List<VehicleAudit> selectByVehicleId(@Param("vehicleId") Long vehicleId, @Param("tenantId") Long tenantId);

    /**
     * 统计指定车辆的待审核数量。
     *
     * @param vehicleId 车辆ID
     * @param tenantId  租户ID
     * @return 待审核数量
     */
    @Select("SELECT COUNT(*) FROM vehicle_audit WHERE vehicle_id = #{vehicleId} AND tenant_id = #{tenantId} AND audit_status = 'PENDING' AND deleted_at IS NULL")
    long countPendingByVehicleId(@Param("vehicleId") Long vehicleId, @Param("tenantId") Long tenantId);
}
