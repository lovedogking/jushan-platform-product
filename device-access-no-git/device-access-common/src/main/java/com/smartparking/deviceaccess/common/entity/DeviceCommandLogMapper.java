package com.smartparking.deviceaccess.common.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备命令执行日志 Mapper。
 * <p>
 * 基于 MyBatis Plus BaseMapper，提供基础 CRUD。
 */
@Mapper
public interface DeviceCommandLogMapper extends BaseMapper<DeviceCommandLog> {
}
