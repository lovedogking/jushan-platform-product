package com.jushan.platform.modules.parking.service;

import com.jushan.platform.modules.parking.entity.FeeRule;
import com.jushan.platform.modules.parking.entity.FeeRuleHistory;
import com.jushan.platform.modules.parking.vo.FeeRuleHistoryVO;

import java.util.List;

/**
 * 收费规则版本历史 Service。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface FeeRuleHistoryService {

    /**
     * 保存规则修改前的快照。
     *
     * @param feeRule 修改前的规则（含时段列表）
     */
    void saveSnapshot(FeeRule feeRule);

    /**
     * 查询某规则的历史版本列表。
     */
    List<FeeRuleHistoryVO> listByFeeRuleId(Long feeRuleId);

    /**
     * 回退到指定历史版本。
     *
     * @param historyId 历史版本 ID
     * @return 回退后的当前规则
     */
    FeeRule rollback(Long historyId);
}
