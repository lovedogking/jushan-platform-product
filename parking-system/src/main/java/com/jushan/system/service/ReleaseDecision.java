package com.jushan.system.service;

/**
 * 出场放行决策值对象（P004）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ReleaseDecision {

    private final String decisionCode;
    private final boolean allowExit;
    private final String reason;

    private ReleaseDecision(String decisionCode, boolean allowExit, String reason) {
        this.decisionCode = decisionCode;
        this.allowExit = allowExit;
        this.reason = reason;
    }

    public static ReleaseDecision paid() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_PAID, true, "已支付，允许出场");
    }

    public static ReleaseDecision zeroFee() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_ZERO_FEE, true, "零元订单，允许出场");
    }

    public static ReleaseDecision unauthorized() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_UNAUTHORIZED, true, "授权车辆，允许出场（P020 扩展）");
    }

    public static ReleaseDecision pendingPayment() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_PENDING_PAYMENT, false, "未支付，禁止自动放行");
    }

    public static ReleaseDecision noRecord() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_NO_RECORD, false, "未找到在场记录");
    }

    public static ReleaseDecision exception(String reason) {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_EXCEPTION, false, reason);
    }

    public String getDecisionCode() {
        return decisionCode;
    }

    public boolean isAllowExit() {
        return allowExit;
    }

    public String getReason() {
        return reason;
    }
}
