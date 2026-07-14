package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.MonitorAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 岗亭监控异常提醒 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface MonitorAlertMapper extends BaseMapper<MonitorAlert> {
}
