package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.FeeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收费规则 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface FeeRuleMapper extends BaseMapper<FeeRule> {

    /**
     * 按车场 ID 查询生效的收费规则列表。
     *
     * @param lotId 车场 ID
     * @return 收费规则列表
     */
    List<FeeRule> selectActiveByLotId(@Param("lotId") Long lotId);

    /**
     * 按车场 ID 和区域 ID 查询收费规则。
     *
     * @param lotId  车场 ID
     * @param zoneId 区域 ID（可为 NULL，表示查询车场通用规则）
     * @return 收费规则列表
     */
    List<FeeRule> selectByLotIdAndZoneId(@Param("lotId") Long lotId, @Param("zoneId") Long zoneId);
}
