package com.smartparking.deviceaccess.api.dto;

import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import com.smartparking.deviceaccess.common.enums.DisplayDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 持久保存显示内容请求 DTO。
 * <p>
 * 将文本写入设备下挂 LED 控制卡外部存储器（Flash），掉电不丢失。
 * 控制卡空闲时自动循环显示已存储内容。低频操作。
 * <p>
 * v0.3 引入。
 * v0.5 增强：支持字体、颜色自定义。
 */
@Data
public class DisplaySaveRequest {

    /**
     * 文本内容。
     * HORIZONTAL 模式下用 {@code \n} 分隔多行。
     */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 文本布局方向 */
    @NotNull(message = "direction 不能为空")
    private DisplayDirection direction;

    /**
     * 字体类型（可选，默认宋体16）。
     * @see OlmM1dProtocol.FontType
     */
    private OlmM1dProtocol.FontType font;

    /**
     * 文字颜色 RGBA（可选，默认白色）。
     * 4 字节数组：[R, G, B, A]
     * <p>
     * 支持每行独立颜色：传入 List，每个元素对应一行的颜色。
     * 如果 List 大小小于行数，剩余行使用最后一个颜色或默认白色。
     */
    private java.util.List<int[]> color;
}
