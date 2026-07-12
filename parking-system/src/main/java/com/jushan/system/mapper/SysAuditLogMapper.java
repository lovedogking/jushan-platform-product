package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
