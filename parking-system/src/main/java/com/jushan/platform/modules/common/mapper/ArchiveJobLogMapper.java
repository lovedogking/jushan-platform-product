package com.jushan.platform.modules.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.common.entity.ArchiveJobLog;
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
