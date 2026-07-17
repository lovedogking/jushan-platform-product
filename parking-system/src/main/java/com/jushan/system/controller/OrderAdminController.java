package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.service.ParkingLotScopeResolver;
import com.jushan.system.service.ParkingOrderService;
import com.jushan.system.vo.OrderAdminVO;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单中心管理 Controller（B1）。
 * <p>
 * 提供运营端订单查询、详情查看、手动关闭和 Excel 导出功能。
 * 统一展示临停订单、月卡续费订单、固定车位续费订单。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class OrderAdminController {

    private static final Logger log = LoggerFactory.getLogger(OrderAdminController.class);

    private static final int EXPORT_MAX_LIMIT = 10000;

    private final ParkingOrderMapper orderMapper;
    private final ParkingOrderService orderService;
    private final ParkingRecordMapper recordMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final SysUserMapper userMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public OrderAdminController(ParkingOrderMapper orderMapper,
                                 ParkingOrderService orderService,
                                 ParkingRecordMapper recordMapper,
                                 ParkingLotMapper parkingLotMapper,
                                 SysUserMapper userMapper,
                                 ParkingLotScopeResolver scopeResolver) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.recordMapper = recordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.userMapper = userMapper;
        this.scopeResolver = scopeResolver;
    }

    // ==================== 常量映射 ====================

    private static final Map<String, String> ORDER_TYPE_LABEL = Map.of(
            ParkingOrder.ORDER_TYPE_PARKING, "临停订单",
            ParkingOrder.ORDER_TYPE_MONTH_RENEW, "月卡续费",
            ParkingOrder.ORDER_TYPE_VISITOR, "访客订单",
            ParkingOrder.ORDER_TYPE_TOP_UP, "充值订单"
    );

    private static final Map<String, String> STATUS_LABEL = Map.of(
            ParkingOrder.STATUS_PENDING_PAY, "待支付",
            ParkingOrder.STATUS_PAYING, "支付中",
            ParkingOrder.STATUS_PAID, "已支付",
            ParkingOrder.STATUS_COMPLETED, "已完成",
            ParkingOrder.STATUS_CANCELLED, "已取消",
            ParkingOrder.STATUS_PAY_FAILED, "支付失败",
            ParkingOrder.STATUS_REFUNDING, "退款中",
            ParkingOrder.STATUS_REFUNDED, "已退款"
    );

    private static final Map<String, String> PAY_CHANNEL_LABEL = Map.of(
            ParkingOrder.PAY_CHANNEL_PYUN, "P云",
            ParkingOrder.PAY_CHANNEL_WECHAT, "微信",
            ParkingOrder.PAY_CHANNEL_ALIPAY, "支付宝",
            ParkingOrder.PAY_CHANNEL_CASH, "现金",
            ParkingOrder.PAY_CHANNEL_BALANCE, "余额"
    );

    /**
     * 分页查询订单列表。
     *
     * @param orderNo       订单号（模糊匹配）
     * @param plateNumber   车牌号（模糊匹配）
     * @param parkingLotId  停车场 ID
     * @param startTime     创建时间起
     * @param endTime       创建时间止
     * @param status        订单状态
     * @param orderType     订单类型
     * @param page          页码
     * @param size          每页大小
     */
    @GetMapping
    @RequirePermission("order:manage")
    public R<IPage<OrderAdminVO>> pageList(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 数据范围：解析当前用户可访问的停车场
        Set<Long> authorizedLotIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedLotIds != null && authorizedLotIds.isEmpty()) {
            // 无授权停车场，返回空结果
            return R.ok(new Page<OrderAdminVO>(page, size).setRecords(Collections.emptyList()).setTotal(0));
        }

        // 如果指定了 parkingLotId，校验权限
        if (parkingLotId != null) {
            scopeResolver.validateAccess(parkingLotId);
        }

        QueryWrapper<ParkingOrder> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.like("order_no", orderNo.trim());
        }
        if (plateNumber != null && !plateNumber.isBlank()) {
            wrapper.like("plate_number", plateNumber.trim().toUpperCase());
        }
        if (parkingLotId != null) {
            wrapper.eq("parking_lot_id", parkingLotId);
        }
        if (startTime != null) {
            wrapper.ge("created_at", startTime);
        }
        if (endTime != null) {
            wrapper.le("created_at", endTime);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (orderType != null && !orderType.isBlank()) {
            wrapper.eq("order_type", orderType);
        }

        // 停车场范围过滤
        if (authorizedLotIds != null) {
            wrapper.in("parking_lot_id", authorizedLotIds);
        }

        wrapper.orderByDesc("created_at");

        IPage<ParkingOrder> orderPage = orderMapper.selectPage(new Page<>(page, size), wrapper);

        List<OrderAdminVO> voList = orderPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        IPage<OrderAdminVO> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        result.setRecords(voList);

        return R.ok(result);
    }

    /**
     * 查看订单详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("order:manage")
    public R<OrderAdminVO> getDetail(@PathVariable Long id) {
        ParkingOrder order = orderMapper.selectById(id);
        if (order == null || order.getDeletedAt() != null) {
            return R.fail(CommonErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }

        // 数据范围校验
        scopeResolver.validateAccess(order.getParkingLotId());
        DataScope.validateTenantMatch(order.getTenantId(), "订单");

        return R.ok(convertToVO(order));
    }

    /**
     * 手动关闭订单。
     * <p>
     * 仅允许关闭待支付（PENDING_PAY）和支付中（PAYING）状态的订单。
     */
    @PostMapping("/{id}/close")
    @RequirePermission("order:manage")
    @BusinessLog(value = "手动关闭订单", module = "order", operationType = "UPDATE",
            operationObject = "订单", objectIdExpression = "#id")
    public R<Map<String, Object>> closeOrder(@PathVariable Long id) {
        ParkingOrder order = orderMapper.selectById(id);
        if (order == null || order.getDeletedAt() != null) {
            return R.fail(CommonErrorCode.NOT_FOUND.getCode(), "订单不存在");
        }

        // 数据范围校验
        scopeResolver.validateAccess(order.getParkingLotId());
        DataScope.validateTenantMatch(order.getTenantId(), "订单");

        // 状态校验：仅允许关闭待支付和支付中
        String currentStatus = order.getStatus();
        if (!ParkingOrder.STATUS_PENDING_PAY.equals(currentStatus)
                && !ParkingOrder.STATUS_PAYING.equals(currentStatus)) {
            return R.fail(CommonErrorCode.BUSINESS_ERROR.getCode(),
                    "订单状态不允许关闭，当前状态: " + STATUS_LABEL.getOrDefault(currentStatus, currentStatus));
        }

        boolean closed = orderService.cancelOrder(id);
        if (closed) {
            log.info("订单手动关闭成功: orderId={}, operator", id);
            return R.ok(Map.of("orderId", id, "status", ParkingOrder.STATUS_CANCELLED));
        }
        return R.fail(CommonErrorCode.BUSINESS_ERROR.getCode(), "订单关闭失败（状态可能已变更）");
    }

    /**
     * Excel 导出订单。
     * <p>
     * 单次导出最多 10,000 条，超过时提示缩小筛选范围。
     */
    @GetMapping("/export")
    @RequirePermission("order:manage")
    @BusinessLog(value = "导出订单", module = "order", operationType = "EXPORT")
    public void exportOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType,
            HttpServletResponse response) throws IOException {

        // 数据范围
        Set<Long> authorizedLotIds = scopeResolver.resolveAuthorizedIds();
        if (authorizedLotIds != null && authorizedLotIds.isEmpty()) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限导出订单\"}");
            return;
        }

        if (parkingLotId != null) {
            scopeResolver.validateAccess(parkingLotId);
        }

        QueryWrapper<ParkingOrder> wrapper = new QueryWrapper<>();
        wrapper.isNull("deleted_at");

        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.like("order_no", orderNo.trim());
        }
        if (plateNumber != null && !plateNumber.isBlank()) {
            wrapper.like("plate_number", plateNumber.trim().toUpperCase());
        }
        if (parkingLotId != null) {
            wrapper.eq("parking_lot_id", parkingLotId);
        }
        if (startTime != null) {
            wrapper.ge("created_at", startTime);
        }
        if (endTime != null) {
            wrapper.le("created_at", endTime);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (orderType != null && !orderType.isBlank()) {
            wrapper.eq("order_type", orderType);
        }
        if (authorizedLotIds != null) {
            wrapper.in("parking_lot_id", authorizedLotIds);
        }
        wrapper.orderByDesc("created_at");

        // 先查总数
        Long total = orderMapper.selectCount(wrapper);
        if (total > EXPORT_MAX_LIMIT) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"message\":\"导出数据超过 " + EXPORT_MAX_LIMIT + " 条，请缩小筛选范围\"}");
            return;
        }

        // 查询数据（限制最大条数）
        List<ParkingOrder> orders = orderMapper.selectList(wrapper.last("LIMIT " + EXPORT_MAX_LIMIT));
        List<OrderAdminVO> voList = orders.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 生成 CSV 导出（不依赖外部库，使用纯 Java 实现）
        String fileName = URLEncoder.encode("订单导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv", StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        // BOM for Excel UTF-8
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        // CSV 表头
        String[] headers = {"订单号", "车牌号", "车场", "入场时间", "出场时间", "停车时长", "应收金额(元)", "实付金额(元)", "状态", "支付方式", "订单类型", "操作人", "创建时间"};
        response.getWriter().println(String.join(",", headers));

        for (OrderAdminVO vo : voList) {
            String[] row = {
                    escapeCsv(vo.getOrderNo()),
                    escapeCsv(vo.getPlateNumber()),
                    escapeCsv(vo.getParkingLotName()),
                    escapeCsv(formatDateTime(vo.getEntryTime())),
                    escapeCsv(formatDateTime(vo.getExitTime())),
                    escapeCsv(formatDuration(vo.getParkingDurationMinutes())),
                    escapeCsv(formatYuan(vo.getPayableAmount())),
                    escapeCsv(formatYuan(vo.getPaidAmount())),
                    escapeCsv(vo.getStatusLabel()),
                    escapeCsv(vo.getPayChannelLabel()),
                    escapeCsv(vo.getOrderTypeLabel()),
                    escapeCsv(vo.getOperatorName()),
                    escapeCsv(formatDateTime(vo.getCreatedAt()))
            };
            response.getWriter().println(String.join(",", row));
        }
        response.getWriter().flush();
    }

    // ==================== 私有方法 ====================

    private OrderAdminVO convertToVO(ParkingOrder order) {
        OrderAdminVO vo = new OrderAdminVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setOrderType(order.getOrderType());
        vo.setOrderTypeLabel(order.getOrderType() != null ? ORDER_TYPE_LABEL.getOrDefault(order.getOrderType(), order.getOrderType()) : null);
        vo.setPlateNumber(order.getPlateNumber());
        vo.setParkingLotId(order.getParkingLotId());
        vo.setAmountCents(order.getAmountCents());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setPointsDiscount(order.getPointsDiscount());
        vo.setPayableAmount(order.getPayableAmount());
        vo.setPaidAmount(order.getPaidAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(STATUS_LABEL.getOrDefault(order.getStatus(), order.getStatus()));
        vo.setPayChannel(order.getPayChannel());
        vo.setPayChannelLabel(order.getPayChannel() != null ? PAY_CHANNEL_LABEL.getOrDefault(order.getPayChannel(), order.getPayChannel()) : null);
        vo.setPayTime(order.getPayTime());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setUpdatedAt(order.getUpdatedAt());

        // 查询停车场名称
        if (order.getParkingLotId() != null) {
            ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(order.getParkingLotId());
            if (lot != null) {
                vo.setParkingLotName(lot.getName());
            }
        }

        // 临停订单：查询停车记录获取入场/出场时间
        if (ParkingOrder.ORDER_TYPE_PARKING.equals(order.getOrderType()) && order.getParkingRecordId() != null) {
            ParkingRecord record = recordMapper.selectById(order.getParkingRecordId());
            if (record != null) {
                vo.setEntryTime(record.getEntryTime());
                vo.setExitTime(record.getExitTime());
                if (record.getEntryTime() != null && record.getExitTime() != null) {
                    vo.setParkingDurationMinutes((int) Duration.between(record.getEntryTime(), record.getExitTime()).toMinutes());
                }
            }
        }

        // 操作人名称
        if (order.getOperatorId() != null) {
            SysUser user = userMapper.selectById(order.getOperatorId());
            if (user != null) {
                vo.setOperatorName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
            }
        }

        return vo;
    }

    private String formatYuan(Integer cents) {
        if (cents == null) return "0.00";
        return String.format("%.2f", cents / 100.0);
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) return "";
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours > 0) {
            return hours + "小时" + mins + "分钟";
        }
        return mins + "分钟";
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        // 如果包含逗号、双引号或换行，需要转义
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
