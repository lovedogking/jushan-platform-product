package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.ExceptionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常记录 Mapper（Phase 2 D2）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ExceptionRecordMapper extends BaseMapper<ExceptionRecord> {
}
