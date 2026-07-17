package com.smartparking.deviceaccess.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.deviceaccess.common.entity.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件发件箱数据访问层。
 *
 * @since v0.4
 */
@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
