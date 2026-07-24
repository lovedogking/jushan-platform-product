package com.jushan.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.tenant.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户主表 Mapper。
 * <p>
 * 系统级表（sys_tenant），不受租户拦截器过滤。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
