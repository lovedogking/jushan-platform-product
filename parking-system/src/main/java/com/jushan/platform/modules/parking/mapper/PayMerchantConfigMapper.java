package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.PayMerchantConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * P云商户配置 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface PayMerchantConfigMapper extends BaseMapper<PayMerchantConfig> {

    /**
     * 按停车场查询生效的商户配置。
     */
    @Select("SELECT * FROM pay_merchant_config WHERE tenant_id = #{tenantId} AND parking_lot_id = #{parkingLotId} AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1")
    PayMerchantConfig selectActiveByParkingLot(@Param("tenantId") Long tenantId, @Param("parkingLotId") Long parkingLotId);
}
