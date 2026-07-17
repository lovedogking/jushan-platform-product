package com.smartparking.deviceaccess.common.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备 Mapper。
 * <p>
 * 基于 MyBatis Plus BaseMapper，提供基础 CRUD。
 * 复杂查询使用 {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper}。
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}
