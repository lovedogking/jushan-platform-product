package com.jushan.platform.modules.parking.enums;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 停车订单状态枚举与状态机守卫（任务包 1-2）。
 * <p>
 * 集中管理订单全部状态与合法流转，替代分散的字符串魔法值。
 * 状态机依据《停车SaaS系统需求规格说明书》附录 10.2（临停订单状态机）：
 * <pre>
 * 预订单 ──出场计费──→ 待支付 ──支付──→ 已支付 ──出场完成──→ 已完成
 *   │                    │
 *   │                    ├─超时关闭──→ 已取消
 *   │                    ├─允许欠费──→ 欠费中 ──补缴──→ 已完成
 *   │                    └─（已支付）退款──→ 已退款
 *   └─免费放行────────────────────────→ 已完成
 * </pre>
 * <p>
 * <strong>守卫约束</strong>：{@link #assertCanTransition(String, String)} 对非法流转
 * 抛出 {@link BusinessException}，禁止任何未在流转表登记的状态跳转（如 CANCELLED → PAID）。
 * 相同状态之间的"流转"视为幂等无操作，由调用方（Service 层）在断言前拦截，本枚举保持纯净的流转表语义。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public enum OrderStatus {

    /** 预订单（入场生成，尚未计费） */
    PRE_ORDER("PRE_ORDER", "预订单"),
    /** 待支付 */
    PENDING_PAY("PENDING_PAY", "待支付"),
    /** 支付中 */
    PAYING("PAYING", "支付中"),
    /** 已支付 */
    PAID("PAID", "已支付"),
    /** 已完成 */
    COMPLETED("COMPLETED", "已完成"),
    /** 已取消（超时关闭等） */
    CANCELLED("CANCELLED", "已取消"),
    /** 支付失败 */
    PAY_FAILED("PAY_FAILED", "支付失败"),
    /** 欠费中 */
    ARREARS("ARREARS", "欠费中"),
    /** 退款中 */
    REFUNDING("REFUNDING", "退款中"),
    /** 已退款 */
    REFUNDED("REFUNDED", "已退款");

    private final String code;
    private final String label;

    OrderStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 合法流转表：key 允许流转到 value 集合中的任一状态。终态映射为空集合。 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // 预订单：出场计费 → 待支付；免费放行 → 已完成
        TRANSITIONS.put(PRE_ORDER, EnumSet.of(PENDING_PAY, COMPLETED));
        // 待支付：支付中 / 已支付（余额或回调直达）/ 零元完成 / 超时取消 / 允许欠费 / 支付失败
        TRANSITIONS.put(PENDING_PAY, EnumSet.of(PAYING, PAID, COMPLETED, CANCELLED, ARREARS, PAY_FAILED));
        // 支付中：已支付 / 取消 / 支付失败
        TRANSITIONS.put(PAYING, EnumSet.of(PAID, CANCELLED, PAY_FAILED));
        // 已支付：出场完成 / 退款
        TRANSITIONS.put(PAID, EnumSet.of(COMPLETED, REFUNDED));
        // 欠费中：补缴 → 已完成
        TRANSITIONS.put(ARREARS, EnumSet.of(COMPLETED));
        // 退款中：已退款
        TRANSITIONS.put(REFUNDING, EnumSet.of(REFUNDED));
        // 终态
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(PAY_FAILED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * 由状态码解析枚举。
     *
     * @param code 状态码
     * @return 对应枚举
     * @throws BusinessException 未知状态码
     */
    public static OrderStatus fromCode(String code) {
        for (OrderStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "未知订单状态: " + code);
    }

    /**
     * 是否允许流转到目标状态（不含相同状态的幂等场景）。
     */
    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    /**
     * 判断两个状态码之间是否为合法流转。
     */
    public static boolean canTransition(String fromCode, String toCode) {
        return fromCode(fromCode).canTransitionTo(fromCode(toCode));
    }

    /**
     * 状态机守卫：非法流转抛出业务异常。
     *
     * @param fromCode 源状态码
     * @param toCode   目标状态码
     * @throws BusinessException 非法流转
     */
    public static void assertCanTransition(String fromCode, String toCode) {
        OrderStatus from = fromCode(fromCode);
        OrderStatus to = fromCode(toCode);
        if (!from.canTransitionTo(to)) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    String.format("非法订单状态流转: %s(%s) → %s(%s)",
                            from.label, from.code, to.label, to.code));
        }
    }

    /**
     * 目标状态集合（只读），用于测试与展示。
     */
    public Set<OrderStatus> allowedTargets() {
        return Collections.unmodifiableSet(TRANSITIONS.getOrDefault(this, Collections.emptySet()));
    }
}
