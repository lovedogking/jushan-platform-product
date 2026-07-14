package com.jushan.system.service;

/**
 * 出场处理结果（P004）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ExitResult {

    private final boolean allowExit;
    private final String decisionCode;
    private final Long exitRecordId;
    private final Long orderId;
    private final int feeCents;
    private final String reason;

    private ExitResult(boolean allowExit, String decisionCode, Long exitRecordId,
                       Long orderId, int feeCents, String reason) {
        this.allowExit = allowExit;
        this.decisionCode = decisionCode;
        this.exitRecordId = exitRecordId;
        this.orderId = orderId;
        this.feeCents = feeCents;
        this.reason = reason;
    }

    public static ExitResult of(ReleaseDecision decision, Long exitRecordId,
                                 Long orderId, int feeCents) {
        return new ExitResult(decision.isAllowExit(), decision.getDecisionCode(),
                exitRecordId, orderId, feeCents, decision.getReason());
    }

    public static ExitResult noRecord(Long exitRecordId, String reason) {
        return new ExitResult(false, com.jushan.system.entity.ExitRecord.DECISION_NO_RECORD,
                exitRecordId, null, 0, reason);
    }

    public boolean isAllowExit() {
        return allowExit;
    }

    public String getDecisionCode() {
        return decisionCode;
    }

    public Long getExitRecordId() {
        return exitRecordId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public int getFeeCents() {
        return feeCents;
    }

    public String getReason() {
        return reason;
    }
}
