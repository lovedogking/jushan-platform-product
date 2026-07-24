package com.jushan.platform.modules.device.client.dto;

/**
 * Device Access v0.4 保存显示内容请求。
 * <p>
 * 对应 {@code POST /api/v1/devices/{deviceId}/display/save} 的请求体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class DisplaySaveRequest {

    /** 显示内容 */
    private String content;

    /** 行号 */
    private Integer lineNumber;

    public DisplaySaveRequest() {}

    public DisplaySaveRequest(String content, Integer lineNumber) {
        this.content = content;
        this.lineNumber = lineNumber;
    }

    // ==================== getter / setter ====================

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
}
