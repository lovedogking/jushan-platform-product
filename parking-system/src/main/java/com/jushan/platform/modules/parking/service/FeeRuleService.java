package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.platform.modules.parking.dto.FeeRuleCreateCmd;
import com.jushan.platform.modules.parking.dto.FeeRuleUpdateCmd;
import com.jushan.platform.modules.parking.vo.FeeRuleVO;

import java.util.List;

/**
 * 收费规则服务接口。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public interface FeeRuleService {

    FeeRuleVO create(FeeRuleCreateCmd cmd);

    FeeRuleVO update(Long ruleId, FeeRuleUpdateCmd cmd);

    boolean removeById(Long ruleId);

    FeeRuleVO detail(Long ruleId);

    IPage<FeeRuleVO> pageList(long current, long size, Long lotId, Long zoneId,
                              Integer billingMode, Integer status);

    FeeRuleVO copy(Long ruleId);

    List<FeeRuleVO> listActiveByLotId(Long lotId);

    FeeRuleVO getActiveRule(Long lotId, Long zoneId);

    void updateStatus(Long ruleId, Integer status);
}
