package com.jushan.platform.modules.miniapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.miniapp.entity.WxUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微信用户 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface WxUserMapper extends BaseMapper<WxUser> {
}