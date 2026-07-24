package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.BillingRuleSwitchLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收费规则切换审计日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface BillingRuleSwitchLogMapper extends BaseMapper<BillingRuleSwitchLog> {
}