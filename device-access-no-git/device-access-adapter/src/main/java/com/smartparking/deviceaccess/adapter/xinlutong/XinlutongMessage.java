package com.smartparking.deviceaccess.adapter.xinlutong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 信路通 MQTT 协议消息体（平铺 JSON）。
 * <p>
 * 与臻识 {@code {id, sn, name, version, bv, payload}} 信封不同，
 * 信路通使用平铺字段：{@code command}、{@code data}、{@code requestId}、{@code time}。
 * <p>
 * 协议文档依据：《信路通路外停车MQTT协议接口规范》 + 真机联调验证。
 *
 * <pre>{@code
 * // 上行示例：心跳（设备 → 平台）
 * {"command":"Rtd", "data":"{\"hasCar\":2}", "requestId":"1783826514072",
 *  "sn":"COPCMD-E10-LS26014528", "time":"2026-07-12 11:21:54", "version":"1.0.1"}
 *
 * // 上行示例：图片上报 / 车牌识别事件（设备 → 平台）
 * // 设备识别到车牌后发送 Image 消息，平台收到后自动下发 Open 命令开闸。
 * // 是否允许开闸的业务判断由设备固件负责。
 * {"command":"Image", "data":"", "requestId":"1783826514071",
 *  "sn":"COPCMD-E10-LS26014528", "time":"2026-07-12 11:21:54", "version":"1.0.1"}
 *
 * // 上行示例：回执（设备 → 平台）
 * {"command":"Result", "data":"{\"result\":0}", "requestId":"<downlink_requestId>",
 *  "sn":"...", "time":"...", "version":"1.0.1"}
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class XinlutongMessage {

    /** 命令名称：Conn / Rtd / Result / Image（真机联调：字段名为 command 非 cmd） */
    @JsonProperty("command")
    private String command;

    /**
     * 命令数据。
     * <p>
     * 对于 Rtd 心跳：{@code {"hasCar":2}}（序列化的 JSON 字符串，需二次解析）。
     * 对于 Image 上报：空字符串 {@code ""}。
     * 对于下行命令（Open / Close）：空字符串 {@code ""}。
     */
    @JsonProperty("data")
    private String data;

    /** 消息时间，格式 "yyyy-MM-dd HH:mm:ss" */
    @JsonProperty("time")
    private String time;

    /**
     * 请求 ID，用于请求-响应关联（真机联调：字段名为 requestId 非 msgId）。
     * <p>
     * 下行命令时由平台生成，设备回执时原样返回。
     * 上行自发消息（如 Rtd、Image）的 requestId 为设备自增序号。
     */
    @JsonProperty("requestId")
    private String requestId;

    /** 设备序列号 */
    @JsonProperty("sn")
    private String sn;

    /** 协议版本号，如 "1.0.1" */
    @JsonProperty("version")
    private String version;
}
