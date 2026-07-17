package com.jushan.system.enums;

import com.jushan.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单状态机单元测试（任务包 1-2）。
 * <p>
 * 覆盖 V1.1 附录 10.2 的合法流转路径与非法流转拦截（状态机守卫）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
class OrderStatusTest {

    @Test
    @DisplayName("附录 10.2 全部合法流转路径均通过")
    void shouldAllowLegalTransitions() {
        // PRE_ORDER --出场计费--> PENDING_PAY
        assertThat(OrderStatus.canTransition("PRE_ORDER", "PENDING_PAY")).isTrue();
        // PENDING_PAY --支付--> PAID
        assertThat(OrderStatus.canTransition("PENDING_PAY", "PAID")).isTrue();
        // PAID --出场完成--> COMPLETED
        assertThat(OrderStatus.canTransition("PAID", "COMPLETED")).isTrue();
        // PENDING_PAY --超时关闭--> CANCELLED
        assertThat(OrderStatus.canTransition("PENDING_PAY", "CANCELLED")).isTrue();
        // PENDING_PAY --允许欠费--> ARREARS
        assertThat(OrderStatus.canTransition("PENDING_PAY", "ARREARS")).isTrue();
        // ARREARS --补缴--> COMPLETED
        assertThat(OrderStatus.canTransition("ARREARS", "COMPLETED")).isTrue();
        // PAID --退款--> REFUNDED
        assertThat(OrderStatus.canTransition("PAID", "REFUNDED")).isTrue();
        // PRE_ORDER --免费放行--> COMPLETED
        assertThat(OrderStatus.canTransition("PRE_ORDER", "COMPLETED")).isTrue();

        // assertCanTransition 对合法流转不抛异常
        OrderStatus.assertCanTransition("PRE_ORDER", "PENDING_PAY");
        OrderStatus.assertCanTransition("PENDING_PAY", "ARREARS");
        OrderStatus.assertCanTransition("ARREARS", "COMPLETED");
        OrderStatus.assertCanTransition("PAID", "REFUNDED");
    }

    @Test
    @DisplayName("非法流转被拦截并抛业务异常（如 CANCELLED→PAID）")
    void shouldRejectIllegalTransitions() {
        assertThat(OrderStatus.canTransition("CANCELLED", "PAID")).isFalse();

        assertThatThrownBy(() -> OrderStatus.assertCanTransition("CANCELLED", "PAID"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OrderStatus.assertCanTransition("COMPLETED", "REFUNDED"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OrderStatus.assertCanTransition("REFUNDED", "PAID"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OrderStatus.assertCanTransition("PAID", "PENDING_PAY"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OrderStatus.assertCanTransition("PRE_ORDER", "PAID"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("终态无任何出边")
    void terminalStatesHaveNoOutgoing() {
        assertThat(OrderStatus.COMPLETED.allowedTargets()).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedTargets()).isEmpty();
        assertThat(OrderStatus.REFUNDED.allowedTargets()).isEmpty();
        assertThat(OrderStatus.PAY_FAILED.allowedTargets()).isEmpty();
    }

    @Test
    @DisplayName("未知状态码抛业务异常")
    void unknownCodeThrows() {
        assertThatThrownBy(() -> OrderStatus.fromCode("NOT_A_STATUS"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("状态码与中文标签映射正确")
    void codeAndLabel() {
        assertThat(OrderStatus.PRE_ORDER.getLabel()).isEqualTo("预订单");
        assertThat(OrderStatus.ARREARS.getLabel()).isEqualTo("欠费中");
        assertThat(OrderStatus.fromCode("REFUNDED")).isEqualTo(OrderStatus.REFUNDED);
    }
}
