package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.FeeRuleSegment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收费规则时段 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface FeeRuleSegmentMapper extends BaseMapper<FeeRuleSegment> {

    /**
     * 按收费规则 ID 查询时段列表。
     *
     * @param feeRuleId 收费规则 ID
     * @return 时段列表
     */
    List<FeeRuleSegment> selectListByFeeRuleId(@Param("feeRuleId") Long feeRuleId);
}
