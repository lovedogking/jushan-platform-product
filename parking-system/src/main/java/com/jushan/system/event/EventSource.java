package com.jushan.system.event;

/**
 * 识别事件来源标识。
 * <p>
 * 每条识别事件必须明确标注来源，用于审计和问题追溯。
 * <ul>
 *   <li>{@link #MANUAL} — 人工通过岗亭或后台手动触发</li>
 *   <li>{@link #MOCK} — 自动化测试或开发环境 Mock 数据</li>
 *   <li>{@link #DEVICE_ACCESS} — Device Access 真实设备上报（当前未冻结，预留）</li>
 * </ul>
 * <p>
 * <strong>P0 红线</strong>：不得将 MOCK 或 MANUAL 来源的事件标注为 DEVICE_ACCESS，
 * 也不得将 Mock 测试结果写成"真机联调通过"。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public enum EventSource {

    /** 人工触发（岗亭/后台） */
    MANUAL,

    /** Mock/测试数据 */
    MOCK,

    /** Device Access 真实设备上报（当前未冻结，预留枚举值） */
    DEVICE_ACCESS
}
