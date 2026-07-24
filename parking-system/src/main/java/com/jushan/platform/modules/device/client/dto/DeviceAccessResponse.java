package com.jushan.platform.modules.device.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Device Access v0.2 统一响应结构。
 * <p>
 * Device Access 当前返回 {@code code=200} 表示成功，与平台前端 {@code code=0} 不同。
 * 客户端内部消费此结构后转换为平台侧业务对象。
 * <p>
 * <strong>注意</strong>：{@code data} 在失败时可能为 null（DA 使用 {@code @JsonInclude(NON_NULL)}）。
 *
 * @param <T> data 的具体类型
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceAccessResponse<T> {

    /** 成功=200；失败为对应错误码（404/503/500 等） */
    private int code;

    /** 结果说明 */
    private String message;

    /** 业务数据（成功时有值，失败时可能为 null） */
    private T data;

    /** 响应时间戳，格式 {@code yyyy-MM-dd HH:mm:ss}，时区 Asia/Shanghai */
    private String timestamp;

    // ==================== 工厂方法 ====================

    /** 判断 DA 响应是否成功（code == 200 且有 data） */
    public boolean isSuccess() {
        return code == 200 && data != null;
    }

    // ==================== getter / setter ====================

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
