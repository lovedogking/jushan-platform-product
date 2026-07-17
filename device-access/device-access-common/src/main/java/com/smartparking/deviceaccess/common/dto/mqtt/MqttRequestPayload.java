package com.smartparking.deviceaccess.common.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台下发命令的 payload 结构。
 * <p>
 * 文档依据：《自定义MQTT协议文档 v1.1.14》第5.2章 下发消息体
 *
 * <pre>{@code
 * "payload": {
 *     "type": "gate_direct_open",
 *     "body": { ... }
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttRequestPayload {

    /** 消息类型，与消息 name 字段一致 */
    @JsonProperty("type")
    private String type;

    /** 消息体，具体结构由各命令决定 */
    @JsonProperty("body")
    private Object body;
}
