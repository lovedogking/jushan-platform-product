package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付流水 Mapper。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /**
     * 按 P云支付流水号查询支付记录。
     */
    @Select("SELECT * FROM pay_order WHERE pay_serial = #{paySerial} AND deleted_at IS NULL LIMIT 1")
    PayOrder selectByPaySerial(@Param("paySerial") String paySerial);

    /**
     * 按支付单号查询支付记录。
     */
    @Select("SELECT * FROM pay_order WHERE pay_order_no = #{payOrderNo} AND deleted_at IS NULL LIMIT 1")
    PayOrder selectByPayOrderNo(@Param("payOrderNo") String payOrderNo);

    /**
     * 按订单ID查询支付记录。
     */
    @Select("SELECT * FROM pay_order WHERE order_id = #{orderId} AND deleted_at IS NULL AND status = 'SUCCESS' LIMIT 1")
    PayOrder selectSuccessByOrderId(@Param("orderId") Long orderId);
}
