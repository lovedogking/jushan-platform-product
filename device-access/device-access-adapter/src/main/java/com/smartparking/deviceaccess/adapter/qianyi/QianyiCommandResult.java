package com.smartparking.deviceaccess.adapter.qianyi;

import lombok.Getter;

/**
 * 芊熠命令执行结果。
 * <p>
 * 芊熠协议应答无数字错误码，仅有 {@code status} 字符串字段：
 * {@code "ok"}（小写）表示成功，其它值为应答端填充的错误描述文本。
 */
@Getter
public class QianyiCommandResult {

    private final boolean success;
    private final String status;

    private QianyiCommandResult(boolean success, String status) {
        this.success = success;
        this.status = status;
    }

    public static QianyiCommandResult success() {
        return new QianyiCommandResult(true, "ok");
    }

    public static QianyiCommandResult failure(String status) {
        return new QianyiCommandResult(false, status != null ? status : "unknown error");
    }

    /**
     * 应答解析失败（无 status 字段或应答为空）。
     */
    public static QianyiCommandResult parseFailure(String rawInfo) {
        return new QianyiCommandResult(false, "parse failure: " + rawInfo);
    }
}
