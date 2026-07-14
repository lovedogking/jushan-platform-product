package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.WxUser;
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