package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.jushan.system.entity.ParkingOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
