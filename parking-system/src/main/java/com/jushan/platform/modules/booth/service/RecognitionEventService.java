package com.jushan.platform.modules.booth.service;

import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;

/**
 * 识别事件处理服务接口。
 * <p>
 * 处理车牌识别事件，包含车辆判定、余位校验、费用计算、开闸决策。
 * 开闸接口为【预留】，当前返回 mock 结果。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface RecognitionEventService {

    /**
     * 处理车牌识别事件。
     * <p>
     * 流程：识别 → 车辆判定 → 余位校验 → 费用计算 → 开闸决策（mock） → 记录日志
     *
     * @param cmd 识别事件命令
     * @return 处理结果
     */
    RecognitionResultVO handleEvent(RecognitionEventCmd cmd);

    /**
     * 人工开闸（预留/mock）。
     * <p>
     * 根据 AGENTS.md 约束：Device Access v0.2 无 gate/open 接口，
     * 开闸为 NOT_IMPLEMENTED_IN_V0.2，HTTP 202 不等于设备执行成功。
     *
     * @param laneId      通道ID
     * @param operatorId  操作人ID
     * @param reason      开闸原因
     * @return 开闸结果（mock）
     */
    RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason);
}
