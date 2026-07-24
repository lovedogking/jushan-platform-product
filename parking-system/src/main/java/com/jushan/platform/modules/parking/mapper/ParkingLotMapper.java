package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 停车场 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingLotMapper extends BaseMapper<ParkingLot> {

    /**
     * 按 ID 查询，忽略租户拦截器。
     * <p>
     * 用于需要先查询实体再做租户归属校验的场景，避免跨租户访问被拦截器直接过滤为 NOT_FOUND。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_lot WHERE id = #{id}")
    ParkingLot selectByIdIgnoreTenant(@Param("id") Long id);
}
