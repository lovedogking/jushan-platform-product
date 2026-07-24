package com.jushan.platform.modules.miniapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.miniapp.entity.MockPaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟支付流水记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface MockPaymentRecordMapper extends BaseMapper<MockPaymentRecord> {
}
