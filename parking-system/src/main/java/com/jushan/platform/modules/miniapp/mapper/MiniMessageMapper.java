package com.jushan.platform.modules.miniapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.miniapp.entity.MiniMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 小程序消息 Mapper（Phase 3 E3）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface MiniMessageMapper extends BaseMapper<MiniMessage> {
}
