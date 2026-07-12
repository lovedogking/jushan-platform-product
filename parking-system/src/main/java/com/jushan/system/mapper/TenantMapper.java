package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
