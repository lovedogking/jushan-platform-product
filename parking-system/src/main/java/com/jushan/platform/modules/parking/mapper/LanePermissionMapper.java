package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.LanePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通道权限配置 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface LanePermissionMapper extends BaseMapper<LanePermission> {

    /**
     * 查询指定通道的所有权限配置。
     *
     * @param laneId   通道ID
     * @param tenantId 租户ID
     * @return 权限列表
     */
    @Select("SELECT * FROM lane_permission WHERE lane_id = #{laneId} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE'")
    List<LanePermission> selectByLaneId(@Param("laneId") Long laneId, @Param("tenantId") Long tenantId);

    /**
     * 查询指定目标的权限配置。
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param tenantId   租户ID
     * @return 权限列表
     */
    @Select("SELECT * FROM lane_permission WHERE target_type = #{targetType} AND target_id = #{targetId} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE'")
    List<LanePermission> selectByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("tenantId") Long tenantId);

    /**
     * 查询指定车辆在所有通道的权限。
     *
     * @param vehicleId 车辆ID
     * @param tenantId  租户ID
     * @return 权限列表
     */
    @Select("SELECT * FROM lane_permission WHERE target_type = 'VEHICLE' AND target_id = #{vehicleId} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE'")
    List<LanePermission> selectByVehicleId(@Param("vehicleId") Long vehicleId, @Param("tenantId") Long tenantId);

    /**
     * 查询指定部门在所有通道的权限。
     *
     * @param departmentId 部门ID
     * @param tenantId     租户ID
     * @return 权限列表
     */
    @Select("SELECT * FROM lane_permission WHERE target_type = 'DEPARTMENT' AND target_id = #{departmentId} AND tenant_id = #{tenantId} AND deleted_at IS NULL AND status = 'ACTIVE'")
    List<LanePermission> selectByDepartmentId(@Param("departmentId") Long departmentId, @Param("tenantId") Long tenantId);
}
