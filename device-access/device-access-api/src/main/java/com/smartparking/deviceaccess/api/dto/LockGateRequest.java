package com.smartparking.deviceaccess.api.dto;

import lombok.Data;

/**
 * 锁定道闸请求 DTO（锁定开闸，继电器强制吸合保持道闸开启）。
 * <p>
 * 注意：硬件仅支持锁定开闸（IO0 高电平锁定）。锁定关闸因电气上 IO0 与 IO1 无互锁而不可行，
 * 该能力已于 v0.4 移除。
 *
 * @since v0.4
 */
@Data
public class LockGateRequest {
    // 仅锁定开闸方向有效，无需 lockType 参数
}
