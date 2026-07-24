package com.jushan.platform.modules.parking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.platform.modules.parking.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单状态流转日志 Mapper（任务包 1-2）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {

    /**
     * 按订单查询完整流转历史（时间升序）。
     * <p>
     * 跳过租户拦截：调用方（运营端订单详情）已先对订单做数据范围/租户校验；
     * MQ / 定时任务等无租户上下文的读取亦可安全使用。
     */
    @InterceptorIgnore(tenantLine = "1")
    @Select("SELECT * FROM order_status_log WHERE order_id = #{orderId} ORDER BY created_at ASC, id ASC")
    List<OrderStatusLog> listByOrderId(@Param("orderId") Long orderId);
}
