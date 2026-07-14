package com.jushan.platform.modules.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 车辆主表 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysVehicleMapper extends BaseMapper<SysVehicle> {

    /**
     * 根据车牌号查询车辆（大写匹配）。
     *
     * @param plateNumber 车牌号
     * @param tenantId    租户ID
     * @return 车辆实体
     */
    @Select("SELECT * FROM sys_vehicle WHERE plate_number = UPPER(#{plateNumber}) AND tenant_id = #{tenantId} AND deleted_at IS NULL LIMIT 1")
    SysVehicle selectByPlateNumber(@Param("plateNumber") String plateNumber, @Param("tenantId") Long tenantId);

    /**
     * 统计指定部门的车辆数量。
     *
     * @param departmentId 部门ID
     * @return 车辆数量
     */
    @Select("SELECT COUNT(*) FROM sys_vehicle WHERE department_id = #{departmentId} AND deleted_at IS NULL")
    long countByDepartmentId(@Param("departmentId") Long departmentId);
}
