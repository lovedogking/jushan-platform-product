package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.FeeRuleHistory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收费规则版本历史 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface FeeRuleHistoryMapper extends BaseMapper<FeeRuleHistory> {

    /**
     * 查询某规则的历史版本列表（按版本号倒序）。
     */
    @Select("SELECT * FROM fee_rule_history WHERE fee_rule_id = #{feeRuleId} AND deleted_at IS NULL ORDER BY version_no DESC")
    List<FeeRuleHistory> selectListByFeeRuleId(@Param("feeRuleId") Long feeRuleId);

    /**
     * 取某规则当前最大版本号。
     */
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM fee_rule_history WHERE fee_rule_id = #{feeRuleId} AND deleted_at IS NULL")
    Integer selectMaxVersionNo(@Param("feeRuleId") Long feeRuleId);
}
