package com.jushan.platform.modules.device.client.dto;

/**
 * Device Access v0.4 显示屏实时文字请求。
 * <p>
 * 对应 {@code POST /api/v1/devices/{deviceId}/display/text} 的请求体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class DisplayTextRequest {

    /** 显示内容 */
    private String content;

    /** 显示方向：HORIZONTAL / VERTICAL */
    private String direction;

    /** 颜色名称：RED / GREEN / YELLOW / WHITE */
    private String colorName;

    public DisplayTextRequest() {}

    public DisplayTextRequest(String content, String direction) {
        this.content = content;
        this.direction = direction;
    }

    public DisplayTextRequest(String content, String direction, String colorName) {
        this.content = content;
        this.direction = direction;
        this.colorName = colorName;
    }

    // ==================== getter / setter ====================

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getColorName() { return colorName; }
    public void setColorName(String colorName) { this.colorName = colorName; }
}
