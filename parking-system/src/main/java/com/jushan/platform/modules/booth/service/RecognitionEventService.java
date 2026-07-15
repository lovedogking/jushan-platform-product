package com.jushan.platform.modules.booth.service;

import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;

/**
 * 识别事件处理服务接口。
 * <p>
 * 处理车牌识别事件，包含车辆判定、余位校验、费用计算、开闸决策。
 * Device Access v0.4 已实现开闸接口，通过 {@link com.jushan.system.client.DeviceAccessClient#openGate} 调用。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface RecognitionEventService {

    /**
     * 处理车牌识别事件。
     * <p>
     * 流程：识别 → 车辆判定 → 余位校验 → 费用计算 → 开闸（v0.4 真实调用） → 记录日志
     *
     * @param cmd 识别事件命令
     * @return 处理结果
     */
    RecognitionResultVO handleEvent(RecognitionEventCmd cmd);

    /**
     * 人工开闸。
     * <p>
     * 通过 Device Access v0.4 {@code POST /api/v1/devices/{deviceSn}/gate/open} 真实调用，
     * 禁止自动重试，失败后由操作员在 UI 层面手动重试。
     *
     * @param laneId      通道ID
     * @param operatorId  操作人ID
     * @param reason      开闸原因
     * @return 开闸结果（含三层状态：gateCommandSent / gateDeviceAck / gateOpened）
     */
    RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason);
}
