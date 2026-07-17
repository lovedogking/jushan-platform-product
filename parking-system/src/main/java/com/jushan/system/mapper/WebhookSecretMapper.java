package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.WebhookSecret;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WebhookSecretMapper extends BaseMapper<WebhookSecret> {

    @Select("SELECT * FROM webhook_secret WHERE parking_lot_id = #{parkingLotId} AND status = 1 LIMIT 1")
    WebhookSecret selectByParkingLotId(Long parkingLotId);
}
