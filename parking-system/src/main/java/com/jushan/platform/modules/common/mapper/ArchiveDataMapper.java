package com.jushan.platform.modules.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.common.entity.ArchiveData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据归档记录 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ArchiveDataMapper extends BaseMapper<ArchiveData> {
}
