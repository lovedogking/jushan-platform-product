package com.smartparking.deviceaccess.api.dto;

import lombok.Data;

/**
 * 解除道闸锁定请求 DTO（解除开闸锁定 + 执行关闸）。
 * <p>
 * 注意：仅解除开闸锁定有效（解锁 IO0 后重置状态机并关闸）。
 *
 * @since v0.4
 */
@Data
public class UnlockGateRequest {
    // 仅解除开闸锁定方向有效，无需 lockType 参数
}
