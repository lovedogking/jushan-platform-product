package com.jushan.platform.modules.booth.service;

import com.jushan.platform.modules.booth.dto.FeeReductionCmd;
import com.jushan.platform.modules.booth.vo.FeeReductionVO;

/**
 * 费用减免服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface FeeReductionService {

    /**
     * 执行费用减免。
     * <p>
     * 校验 session 存在且费用有效，
     * 验证 reducedFeeCents <= originalFeeCents，
     * 更新费用，写入审计日志。
     *
     * @param cmd 减免命令
     * @return 减免结果
     */
    FeeReductionVO apply(FeeReductionCmd cmd);
}
