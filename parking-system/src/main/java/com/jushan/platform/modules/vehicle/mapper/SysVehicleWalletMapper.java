package com.jushan.platform.modules.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 储值车钱包 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysVehicleWalletMapper extends BaseMapper<SysVehicleWallet> {

    /**
     * 根据车辆ID查询钱包。
     *
     * @param vehicleId 车辆ID
     * @param tenantId  租户ID
     * @return 钱包实体
     */
    @Select("SELECT * FROM sys_vehicle_wallet WHERE vehicle_id = #{vehicleId} AND tenant_id = #{tenantId} AND deleted_at IS NULL LIMIT 1")
    SysVehicleWallet selectByVehicleId(@Param("vehicleId") Long vehicleId, @Param("tenantId") Long tenantId);
}
