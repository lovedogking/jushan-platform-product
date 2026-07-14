package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.ArchiveJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 归档任务执行日志 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ArchiveJobLogMapper extends BaseMapper<ArchiveJobLog> {
}
