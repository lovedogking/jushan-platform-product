package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

/**
 * 停车场档案 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
@Repository("modulesParkingLotMapper")
public interface ParkingLotMapper extends BaseMapper<ParkingLot> {

    /**
     * 按 ID 查询，忽略租户拦截器。
     * <p>
     * 用于需要先查询实体再做租户归属校验的场景。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_lot WHERE id = #{id}")
    ParkingLot selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 按名称查询，忽略租户拦截器（用于唯一性校验）。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_lot WHERE tenant_id = #{tenantId} AND name = #{name} AND deleted_at IS NULL LIMIT 1")
    ParkingLot selectByNameIgnoreTenant(@Param("tenantId") Long tenantId, @Param("name") String name);
}
