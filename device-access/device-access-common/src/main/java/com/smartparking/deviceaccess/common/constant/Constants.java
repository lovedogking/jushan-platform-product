package com.smartparking.deviceaccess.common.constant;

/**
 * 全局常量。
 * <p>
 * 仅放置跨模块共享的常量；模块内部常量放在各自模块的 constant 包中。
 */
public final class Constants {

    private Constants() {
    }

    /** 品牌标识 —— v0.1 仅臻识 */
    public static final String BRAND_ZHENSHI = "ZHENSHI";

    /** MQTT Topic 前缀（待臻识官方文档确认具体格式） */
    public static final String MQTT_TOPIC_PREFIX = "zhenshi/c5h";
}
