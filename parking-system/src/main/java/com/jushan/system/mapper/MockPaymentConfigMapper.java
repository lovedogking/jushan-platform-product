package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.MockPaymentConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟支付车场配置 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface MockPaymentConfigMapper extends BaseMapper<MockPaymentConfig> {
}
