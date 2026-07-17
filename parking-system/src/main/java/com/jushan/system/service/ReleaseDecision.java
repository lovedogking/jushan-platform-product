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

    /** 欠费放行：出口未支付但车场策略允许欠费出场 */
    public static ReleaseDecision arrearsAllowed() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_ARREARS_ALLOWED, true, "车场配置允许欠费放行");
    }

    /** 欠费提醒放行：再次出场时有欠费订单，策略为 REMIND_ONLY */
    public static ReleaseDecision arrearsRemind() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_ARREARS_REMIND, true, "欠费提醒放行，订单保持欠费中");
    }

    /** 欠费合并计费：再次出场时存在欠费订单，需一并补缴 */
    public static ReleaseDecision arrearsMustPay() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_ARREARS_MUST_PAY, false, "存在欠费订单，需补缴欠费+本次费用");
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
