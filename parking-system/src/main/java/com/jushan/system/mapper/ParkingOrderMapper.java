package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.jushan.system.entity.ParkingOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 停车订单 Mapper（P004 骨架）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ParkingOrderMapper extends BaseMapper<ParkingOrder> {

    /**
     * 查询指定前缀的最大订单号。
     */
    @Select("SELECT MAX(order_no) FROM parking_order WHERE order_no LIKE CONCAT(#{prefix}, '%') AND deleted_at IS NULL")
    String selectMaxOrderNoByPrefix(String prefix);

    /**
     * 按订单号查询订单（支付回调使用，跳过租户拦截）。
     */
    @InterceptorIgnore(tenantLine = "1")
    @Select("SELECT * FROM parking_order WHERE order_no = #{orderNo} AND deleted_at IS NULL LIMIT 1")
    ParkingOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 更新订单过期时间。
     * 仅当订单当前为 PENDING_PAY 状态时更新。
     */
    @Update("UPDATE parking_order SET expired_at = #{expiredAt}, updated_at = NOW() " +
            "WHERE id = #{orderId} AND status = 'PENDING_PAY' AND deleted_at IS NULL")
    int updateOrderExpiry(@Param("orderId") Long orderId, @Param("expiredAt") LocalDateTime expiredAt);

    /**
     * 标记订单为已支付。
     * 仅当订单当前为 PENDING_PAY 或 PAYING 状态时更新。
     */
    @Update("UPDATE parking_order SET status = 'PAID', pay_serial = #{paySerial}, " +
            "paid_amount = #{paidAmount}, pay_time = #{payTime}, updated_at = NOW() " +
            "WHERE id = #{orderId} AND status IN ('PENDING_PAY', 'PAYING') AND deleted_at IS NULL")
    int markPaidStatus(@Param("orderId") Long orderId,
                       @Param("paySerial") String paySerial,
                       @Param("paidAmount") Integer paidAmount,
                       @Param("payTime") LocalDateTime payTime);

    /**
     * 取消订单（待支付或支付中）。
     * 仅当订单当前为 PENDING_PAY 或 PAYING 状态时取消。
     */
    @Update("UPDATE parking_order SET status = 'CANCELLED', updated_at = NOW() " +
            "WHERE id = #{orderId} AND status IN ('PENDING_PAY', 'PAYING') AND deleted_at IS NULL AND expired_at IS NOT NULL AND expired_at < NOW()")
    int cancelExpiredOrder(@Param("orderId") Long orderId);
}
