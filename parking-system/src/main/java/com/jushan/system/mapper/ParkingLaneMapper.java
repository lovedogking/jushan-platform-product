package com.jushan.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.ParkingLane;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 车道 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingLaneMapper extends BaseMapper<ParkingLane> {

    /**
     * 按 ID 查询，忽略租户拦截器。
     * <p>
     * 用于需要先查询实体再做停车场/租户归属校验的场景。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM parking_lane WHERE id = #{id}")
    ParkingLane selectByIdIgnoreTenant(@Param("id") Long id);
}
