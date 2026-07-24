package com.jushan.platform.modules.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.device.entity.MonitorAlert;
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
