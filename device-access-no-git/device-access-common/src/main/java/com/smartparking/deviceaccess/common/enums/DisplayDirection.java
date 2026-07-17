package com.smartparking.deviceaccess.common.enums;

import lombok.Getter;

/**
 * 文本布局方向枚举。
 * <p>
 * 表示平台希望文字在显示屏上的排列方向。
 * 这是文本布局意图，不是控制卡屏幕方向参数。
 * OLM-M1D 协议中不存在屏幕旋转/方向切换命令。
 * <p>
 * Adapter 层根据此枚举将文本转换为对应的 LID 分配。
 * <p>
 * v0.3 引入，属于 Camera 的 DISPLAY 外围能力控制。
 *
 * @see PeripheralType#DISPLAY
 */
@Getter
public enum DisplayDirection {

    /** 横向排列：文字按行展开，\n 分隔多行 */
    HORIZONTAL("横向"),

    /** 竖向排列：文字按列展开（暂未实现） */
    VERTICAL("竖向");

    private final String description;

    DisplayDirection(String description) {
        this.description = description;
    }
}
