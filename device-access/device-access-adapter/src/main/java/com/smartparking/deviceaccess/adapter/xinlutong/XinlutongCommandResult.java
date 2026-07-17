package com.smartparking.deviceaccess.adapter.xinlutong;

import lombok.Builder;
import lombok.Getter;

/**
 * 信路通命令执行结果。
 * <p>
 * 由 {@link XinlutongMessageHandler} 从厂商回执中解析得出，
 * 供 {@link com.smartparking.deviceaccess.api.XinlutongDeviceCoordinator} 消费。
 * v0.4 新增。
 */
@Getter
@Builder
public class XinlutongCommandResult {

    /** 是否成功 */
    private final boolean success;

    /** 厂商错误码 */
    private final int errCode;

    /** 厂商错误信息 */
    private final String errInfo;

    /**
     * 创建成功结果。
     */
    public static XinlutongCommandResult success() {
        return XinlutongCommandResult.builder()
                .success(true)
                .errCode(0)
                .errInfo("")
                .build();
    }

    /**
     * 创建失败结果。
     */
    public static XinlutongCommandResult failure(int errCode, String errInfo) {
        return XinlutongCommandResult.builder()
                .success(false)
                .errCode(errCode)
                .errInfo(errInfo)
                .build();
    }

    /**
     * 创建解析失败结果。
     */
    public static XinlutongCommandResult parseFailure(String rawData) {
        return XinlutongCommandResult.builder()
                .success(false)
                .errCode(-1)
                .errInfo("Failed to parse reply data: " + rawData)
                .build();
    }
}
