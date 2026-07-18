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
     * <p>
     * 当 {@code isCharge} 为 {@code true} 时，{@code feeCents} 和 {@code plateNumber}
     * 会被写入设备命令审计记录（{@code device_command_audit}），
     * 用于记录手工计费或免费放行的审计信息。
     *
     * @param laneId      通道ID
     * @param operatorId  操作人ID
     * @param reason      开闸原因
     * @param isCharge    是否计费（true=计费开闸，false=免费放行）
     * @param feeCents    计费金额（分），isCharge=true 时有效
     * @param plateNumber 车牌号（可选，用于审计记录）
     * @return 开闸结果（含三层状态：gateCommandSent / gateDeviceAck / gateOpened）
     */
    RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason,
                                       boolean isCharge, Integer feeCents, String plateNumber);

    /**
     * 人工关闸。
     * <p>
     * 通过 GPIO 或 Device Access 关闸，与开闸共享同一设备查找逻辑。
     *
     * @param laneId      通道ID
     * @param operatorId  操作人ID
     * @param reason      关闸原因
     * @return 关闸结果
     */
    RecognitionResultVO manualCloseGate(Long laneId, Long operatorId, String reason);
}
