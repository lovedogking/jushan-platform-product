package com.jushan.platform.modules.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.vehicle.entity.BindingPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车辆绑定策略 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface BindingPolicyMapper extends BaseMapper<BindingPolicy> {
}