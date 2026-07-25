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
     * @param entryImage  入场抓拍图 URL（可选，由前端手动抓拍提供）
     * @return 开闸结果（含三层状态：gateCommandSent / gateDeviceAck / gateOpened）
     */
    RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason,
                                       boolean isCharge, Integer feeCents, String plateNumber,
                                       String entryImage, Integer direction);

    /**
     * 人工开闸（向后兼容，不带抓拍图和方向）。
     */
    default RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason,
                                               boolean isCharge, Integer feeCents, String plateNumber,
                                               String entryImage) {
        return manualOpenGate(laneId, operatorId, reason, isCharge, feeCents, plateNumber, null, null);
    }

    default RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason,
                                               boolean isCharge, Integer feeCents, String plateNumber) {
        return manualOpenGate(laneId, operatorId, reason, isCharge, feeCents, plateNumber, null, null);
    }

    /**
     * 主动抓拍指定车道的相机。
     * <p>
     * 选择车道主相机（或唯一相机）触发抓拍，返回图片 URL。
     *
     * @param laneId 通道 ID
     * @return 抓拍结果（含图片 URL）
     */
    /**
     * 手动抓拍指定车道相机。
     *
     * @param laneId    通道 ID
     * @param direction 识别方向（1=入口, 2=出口），null 时默认入口
     */
    com.jushan.platform.modules.device.client.dto.CaptureResultDTO captureImage(Long laneId, Integer direction);

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

    /**
     * 常开（锁定道闸，继电器强制吸合保持开启）。
     *
     * @param laneId     通道ID
     * @param operatorId 操作人ID
     * @param reason     常开原因
     * @return 操作结果
     */
    RecognitionResultVO manualLockGate(Long laneId, Long operatorId, String reason);

    /**
     * 取消常开（解除道闸锁定并关闸，恢复常规模式）。
     *
     * @param laneId     通道ID
     * @param operatorId 操作人ID
     * @param reason     取消原因
     * @return 操作结果
     */
    RecognitionResultVO manualUnlockGate(Long laneId, Long operatorId, String reason);

    /**
     * 常关（锁定道闸关闭，继电器强制保持关闭）。
     * <p>
     * 与 {@link #manualLockGate} 对称：常关成功后，道闸将持续保持关闭状态，
     * 白名单车辆也不会自动开闸。取消常关复用 {@link #manualUnlockGate}（恢复到 AUTO）。
     *
     * @param laneId     通道ID
     * @param operatorId 操作人ID
     * @param reason     常关原因
     * @return 操作结果
     * @since v1.5
     */
    RecognitionResultVO manualLockCloseGate(Long laneId, Long operatorId, String reason);

    /**
     * 取消常关（解除道闸关闭锁定，恢复常规模式）。
     * <p>
     * 底层与 {@link #manualUnlockGate} 一样调用 unlockGate 命令（设备只认 unlock），
     * 但通过独立端点区分审计语义：操作员是"取消常关"而非"取消常开"。
     *
     * @param laneId     通道ID
     * @param operatorId 操作人ID
     * @param reason     取消原因
     * @return 操作结果
     * @since v1.5
     */
    RecognitionResultVO manualUnlockCloseGate(Long laneId, Long operatorId, String reason);
}
