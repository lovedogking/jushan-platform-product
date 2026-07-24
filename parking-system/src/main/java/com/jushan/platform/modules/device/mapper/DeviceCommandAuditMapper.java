package com.jushan.platform.modules.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.device.entity.DeviceCommandAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备命令调用审计 Mapper（P001）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface DeviceCommandAuditMapper extends BaseMapper<DeviceCommandAudit> {
}
