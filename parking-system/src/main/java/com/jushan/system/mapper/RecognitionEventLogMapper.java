package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.RecognitionEventLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 识别事件日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface RecognitionEventLogMapper extends BaseMapper<RecognitionEventLog> {
}
