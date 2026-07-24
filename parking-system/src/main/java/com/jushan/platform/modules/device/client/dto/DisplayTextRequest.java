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

    /** 字体大小 */
    private Integer fontSize;

    /** 文字颜色 */
    private String color;

    public DisplayTextRequest() {}

    public DisplayTextRequest(String content, String direction, Integer fontSize, String color) {
        this.content = content;
        this.direction = direction;
        this.fontSize = fontSize;
        this.color = color;
    }

    // ==================== getter / setter ====================

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
