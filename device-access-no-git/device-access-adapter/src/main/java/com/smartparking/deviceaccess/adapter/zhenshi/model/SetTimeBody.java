package com.smartparking.deviceaccess.adapter.zhenshi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * set_time 命令的 body。
 * <p>
 * 文档依据：《自定义MQTT协议文档 v1.1.14》第7.5章 设置时间
 */
@Data
@Builder
public class SetTimeBody {

    /** 年，取值 [1970, 2036] */
    @JsonProperty("year")
    private String year;

    /** 月，取值 [1, 12] */
    @JsonProperty("month")
    private String month;

    /** 日，取值 [1, 31] */
    @JsonProperty("day")
    private String day;

    /** 时，取值 [0, 23] */
    @JsonProperty("hour")
    private String hour;

    /** 分，取值 [0, 59] */
    @JsonProperty("min")
    private String min;

    /** 秒，取值 [0, 59] */
    @JsonProperty("sec")
    private String sec;
}
