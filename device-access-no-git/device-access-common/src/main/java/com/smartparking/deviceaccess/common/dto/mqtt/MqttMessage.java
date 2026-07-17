package com.smartparking.deviceaccess.common.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 臻识 MQTT 协议通用消息信封。
 * <p>
 * 同时用于设备上报和平台下发两种场景。
 * <ul>
 *   <li>设备上报：payload 中包含具体业务数据（如识别结果）</li>
 *   <li>平台下发（请求）：payload 中包含 type + body</li>
 *   <li>设备回复（响应）：code=200 表示成功</li>
 * </ul>
 * <p>
 * 文档依据：《自定义MQTT协议文档 v1.1.14》第5章 消息体说明
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttMessage {

    /** 消息 ID，UUID 格式，用于关联请求和响应 */
    @JsonProperty("id")
    private String id;

    /** 协议版本号 */
    @JsonProperty("bv")
    private Integer bv;

    /** 设备序列号 */
    @JsonProperty("sn")
    private String sn;

    /** 消息类型，如 ivs_result / gate_direct_open / keep_alive */
    @JsonProperty("name")
    private String name;

    /** 消息版本号，当前固定为 "1.0" */
    @JsonProperty("version")
    private String version;

    /** Unix 时间戳（秒） */
    @JsonProperty("timestamp")
    private Long timestamp;

    /** 响应状态码，仅设备回复消息中有值，200 表示成功 */
    @JsonProperty("code")
    private Integer code;

    /** 消息载荷（JSON Object） */
    @JsonProperty("payload")
    private Object payload;
}
