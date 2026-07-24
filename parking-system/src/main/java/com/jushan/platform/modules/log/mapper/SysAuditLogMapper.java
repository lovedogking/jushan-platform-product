package com.jushan.platform.modules.log.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.log.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审计日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    /**
     * 按 ID 查询，忽略租户拦截器。
     * <p>
     * 审计日志详情需区分平台用户（可跨租户）和租户用户（仅本租户），因此先查询再做数据范围校验。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_audit_log WHERE id = #{id}")
    SysAuditLog selectByIdIgnoreTenant(@Param("id") Long id);
}
