package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 设备能力枚举。
 * <p>
 * 表示设备产品型号所支持的控制能力。
 * 能力绑定在 {@link com.smartparking.deviceaccess.common.entity.DeviceProduct} 上，
 * 由产品型号决定，不代表设备在线状态。
 * <p>
 * v0.3 引入，替代 deviceType 硬编码判断。
 */
@Getter
public enum DeviceCapability {

    /** 实时显示文字（临时区） */
    DISPLAY_TEXT,

    /** 持久保存显示内容（存储区） */
    DISPLAY_SAVE,

    /** 外围设备控制（启用/关闭/模式切换） */
    PERIPHERAL_CONTROL,

    /** 校时 */
    TIME_SYNC,

    /** 开闸 */
    OPEN_GATE,

    /** 关闸 */
    CLOSE_GATE,

    /** 显示屏配置（音量、亮度、方向、时间同步） */
    DISPLAY_CONFIG,

    /** 语音控制（播放、停止） */
    VOICE_CONTROL,

    /** 增强显示（支持字体、颜色、语音同步） */
    DISPLAY_ENHANCED,

    /** 锁定开闸（继电器强制吸合，保持道闸开启） */
    LOCK_OPEN_GATE,

    /** 主动抓拍 */
    CAPTURE
}
