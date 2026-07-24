package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.DuplicateEntryLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常重复入场记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface DuplicateEntryLogMapper extends BaseMapper<DuplicateEntryLog> {
}
