package com.jushan.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 平台设备台账 Mapper（T20）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 按 ID 查询，忽略租户拦截器。
     * <p>
     * 用于需要先查询实体再做停车场/租户归属校验的场景。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM device WHERE id = #{id}")
    Device selectByIdIgnoreTenant(@Param("id") Long id);

    /**
     * 按设备序列号查询，忽略租户拦截器。
     * <p>
     * 用于 Webhook 等场景：根据 Device Access 推送的 deviceSn
     * 反查平台设备台账，推导可信的 tenantId/parkingLotId/laneId。
     * deviceSn 在同厂商内唯一，但跨厂商可能重复；查询结果取第一条匹配。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM device WHERE device_sn = #{deviceSn} AND status = 'ENABLED' LIMIT 1")
    Device selectByDeviceSn(@Param("deviceSn") String deviceSn);
}
