package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ExitRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出场记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ExitRecordMapper extends BaseMapper<ExitRecord> {
}
