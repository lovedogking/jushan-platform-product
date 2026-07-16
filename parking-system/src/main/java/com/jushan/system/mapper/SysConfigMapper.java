package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link SysConfig} Mapper。
 * <p>
 * sys_config 为全局表，无租户隔离。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
