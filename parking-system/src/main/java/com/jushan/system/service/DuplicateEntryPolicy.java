package com.jushan.system.service;

/**
 * 重复入场处理策略枚举（P003）。
 * <p>
 * 当同一车辆在已有未结（PARKING 状态）停车记录时再次触发入口识别，
 * 按停车场配置的策略执行不同处理。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public enum DuplicateEntryPolicy {

    /**
     * 拒绝并记录异常（默认策略）。
     * <p>
     * 保留现有行为：抛出业务异常，事件标记 FAILED，
     * 不创建新停车记录，不更新停车场容量。
     * 运营人员可通过事件日志查看被拒绝的重复入场。
     */
    REJECT,

    /**
     * 更新原记录入场时间和抓拍。
     * <p>
     * 将已有 PARKING 记录的入场时间更新为本次事件时间，
     * 更新入场车道/设备/抓拍图片，保留原记录 ID。
     * 适用于：车辆在场期间因异常原因（如系统重启、设备误触发）
     * 产生重复识别，希望以最新识别为准。
     * <p>
     * <strong>安全约束</strong>：
     * <ul>
     *   <li>仅更新指定字段（entry_time, lane_id, device_id, entry_event_id, entry_image_path），
     *       不修改 status、exit_time、fee_rule_version 等</li>
     *   <li>使用条件更新 WHERE id = ? AND status = 'PARKING' 防止并发竞态</li>
     *   <li>不增加 currentVehicles（车辆数不变）</li>
     * </ul>
     */
    UPDATE,

    /**
     * 创建异常待处理记录。
     * <p>
     * 将已有 PARKING 记录标记为 EXCEPTION（异常待处理），
     * 同时创建新的 PARKING 记录。
     * 原记录保留供运营人员核查（如判断是否为跟车、套牌等异常）。
     * <p>
     * <strong>安全约束</strong>：
     * <ul>
     *   <li>原记录状态由 PARKING → EXCEPTION（条件更新，防止并发覆盖）</li>
     *   <li>新记录正常创建，容量 +1（因为视为新的在场车辆）</li>
     *   <li>两条记录均关联同一车牌，运营人员需人工确认后处置</li>
     * </ul>
     */
    EXCEPTION
}
