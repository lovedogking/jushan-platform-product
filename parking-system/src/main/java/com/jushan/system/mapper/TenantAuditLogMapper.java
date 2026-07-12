package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.TenantAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户审核日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface TenantAuditLogMapper extends BaseMapper<TenantAuditLog> {
}
