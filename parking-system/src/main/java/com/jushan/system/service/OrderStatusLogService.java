package com.jushan.system.service;

import com.jushan.system.entity.OrderStatusLog;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.mapper.OrderStatusLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态流转日志服务（任务包 1-2）。
 * <p>
 * 统一写入 {@code order_status_log}，供订单状态机各流转点调用。
 * 写入与业务流转处于同一事务，保证"状态变更必留痕"，避免日志缺失。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class OrderStatusLogService {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusLogService.class);

    private final OrderStatusLogMapper mapper;

    public OrderStatusLogService(OrderStatusLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录一次状态流转。
     *
     * @param order         订单（提供 tenantId/parkingLotId/orderNo）
     * @param fromStatus    源状态（创建时可为 null）
     * @param toStatus      目标状态
     * @param triggerSource 触发源，见 {@link OrderStatusLog} 常量
     * @param operatorId    操作人 ID（系统/定时任务可为 null）
     * @param operatorName  操作人名称/标记（可为 null）
     * @param remark        备注（如退款原因，可为 null）
     */
    public void record(ParkingOrder order, String fromStatus, String toStatus,
                       String triggerSource, Long operatorId, String operatorName, String remark) {
        if (order == null || order.getId() == null) {
            log.warn("跳过状态流转日志：订单为空 fromStatus={} toStatus={}", fromStatus, toStatus);
            return;
        }
        OrderStatusLog entry = new OrderStatusLog();
        entry.setTenantId(order.getTenantId());
        entry.setParkingLotId(order.getParkingLotId());
        entry.setOrderId(order.getId());
        entry.setOrderNo(order.getOrderNo());
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus);
        entry.setTriggerSource(triggerSource);
        entry.setOperatorId(operatorId);
        entry.setOperatorName(operatorName);
        entry.setRemark(remark);
        entry.setCreatedAt(LocalDateTime.now());
        mapper.insert(entry);
    }

    /**
     * 系统触发的流转日志（无操作人）。
     */
    public void recordSystem(ParkingOrder order, String fromStatus, String toStatus, String remark) {
        record(order, fromStatus, toStatus, OrderStatusLog.TRIGGER_SYSTEM, null, null, remark);
    }

    /**
     * 查询订单的状态流转历史（时间升序）。
     */
    public List<OrderStatusLog> listByOrderId(Long orderId) {
        return mapper.listByOrderId(orderId);
    }
}
