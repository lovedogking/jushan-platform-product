package com.jushan.platform.modules.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.log.entity.SysBusinessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务操作日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysBusinessLogMapper extends BaseMapper<SysBusinessLog> {
}
