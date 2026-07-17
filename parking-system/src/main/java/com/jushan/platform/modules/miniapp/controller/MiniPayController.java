package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.dto.MiniPayNotifyRequest;
import com.jushan.platform.modules.miniapp.dto.MiniPayPrepareRequest;
import com.jushan.platform.modules.miniapp.dto.ProxyPayRequest;
import com.jushan.platform.modules.miniapp.entity.MiniMessage;
import com.jushan.platform.modules.miniapp.entity.ProxyPayRecord;
import com.jushan.platform.modules.miniapp.mapper.MiniMessageMapper;
import com.jushan.platform.modules.miniapp.mapper.ProxyPayRecordMapper;
import com.jushan.platform.modules.miniapp.service.MiniMessageService;
import com.jushan.platform.modules.miniapp.vo.MiniPayResultVO;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.WxUser;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.WxUserMapper;
import com.jushan.system.service.BillingEngine;
import com.jushan.system.service.MockPaymentService;
import com.jushan.system.service.ParkingFeeService;
import com.jushan.system.service.ParkingOrderService;
import com.jushan.system.service.WxUserService;
import com.jushan.system.vo.ParkingFeeVO;
import com.jushan.system.ws.BoothWebSocketPublisher;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小程序支付控制器。
 * <p>
 * 提供小程序停车缴费的预下单和支付确认能力，以及代缴功能（Phase 3 E1）。
 * <p>
 * <strong>Phase 0 改造</strong>：已切换为模拟支付流程（{@link MockPaymentService}），
 * 不再依赖真实支付平台（4pyun.com/PyunPaymentClient）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/mini/pay")
public class MiniPayController {

    private static final Logger log = LoggerFactory.getLogger(MiniPayController.class);

    /** 前端调用标记 */
    private static final String PAID_BY_MINIAPP = "miniapp";
    /** 代缴标记 */
    private static final String PAID_BY_PROXY_PREFIX = "proxy_";

    private final ParkingFeeService parkingFeeService;
    private final ParkingOrderService orderService;
    private final ParkingOrderMapper orderMapper;
    private final ParkingRecordMapper recordMapper;
    private final MockPaymentService mockPaymentService;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final BillingEngine billingEngine;
    private final WxUserService wxUserService;
    private final WxUserMapper wxUserMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ProxyPayRecordMapper proxyPayRecordMapper;
    private final MiniMessageService miniMessageService;

    public MiniPayController(ParkingFeeService parkingFeeService,
                              ParkingOrderService orderService,
                              ParkingOrderMapper orderMapper,
                              ParkingRecordMapper recordMapper,
                              MockPaymentService mockPaymentService,
                              BoothWebSocketPublisher boothWebSocketPublisher,
                              BillingEngine billingEngine,
                              WxUserService wxUserService,
                              WxUserMapper wxUserMapper,
                              ParkingLotMapper parkingLotMapper,
                              ProxyPayRecordMapper proxyPayRecordMapper,
                              MiniMessageService miniMessageService) {
        this.parkingFeeService = parkingFeeService;
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.recordMapper = recordMapper;
        this.mockPaymentService = mockPaymentService;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.billingEngine = billingEngine;
        this.wxUserService = wxUserService;
        this.wxUserMapper = wxUserMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.proxyPayRecordMapper = proxyPayRecordMapper;
        this.miniMessageService = miniMessageService;
    }

    /**
     * 支付预下单（模拟支付）。
     * <p>
     * 计算费用、创建订单、创建模拟支付记录。
     * 不再调用真实支付平台，小程序无需处理 wx.requestPayment 参数。
     *
     * @param request 预下单请求（含停车记录 ID）
     * @return 预支付结果
     */
    @PostMapping("/prepare")
    @RequirePermission("miniapp:view")
    public R<MiniPayResultVO> preparePay(@Valid @RequestBody MiniPayPrepareRequest request) {
        // 1. 查询费用（同时校验车牌归属）
        ParkingFeeVO feeVo = parkingFeeService.queryByRecordIdForWx(request.getRecordId());

        if (!Boolean.TRUE.equals(feeVo.getPayable())) {
            return R.fail(4002, "该记录不可支付（" + (feeVo.getMessage() != null ? feeVo.getMessage() : "已离场或已取消") + "）");
        }

        if (feeVo.getFeeCents() != null && feeVo.getFeeCents() <= 0) {
            return R.fail(4002, "无需支付（当前仍在免费时段内）");
        }

        // 2. 复用已有订单（任务包 1-2：优先复用入场预订单，保证入场→查费→缴费→出场全链路单一订单）
        if (Boolean.TRUE.equals(feeVo.getHasPendingOrder()) && feeVo.getPendingOrderId() != null) {
            ParkingOrder pendingOrder = orderService.getById(feeVo.getPendingOrderId());
            if (pendingOrder != null && ParkingOrder.STATUS_PRE_ORDER.equals(pendingOrder.getStatus())) {
                // 提前缴费：将入场预订单按当前费用计费置待支付，再走模拟支付
                int preFeeCents = feeVo.getFeeCents() != null ? feeVo.getFeeCents() : 0;
                orderService.preOrderToPending(pendingOrder.getId(), preFeeCents,
                        LocalDateTime.now().plusMinutes(15));
                pendingOrder = orderService.getById(pendingOrder.getId());
                orderService.startPaying(pendingOrder.getId(), ParkingOrder.PAY_CHANNEL_PYUN);
                mockPaymentService.preparePay(pendingOrder);
                return buildPrepareResult(pendingOrder);
            }
            if (pendingOrder != null && ParkingOrder.STATUS_PENDING_PAY.equals(pendingOrder.getStatus())) {
                return buildPrepareResult(pendingOrder);
            }
        }

        // 3. 获取停车记录
        ParkingRecord record = recordMapper.selectById(request.getRecordId());
        if (record == null) {
            return R.fail(1003, "停车记录不存在");
        }

        // 4. 创建订单
        String idempotencyKey = "MINI_" + request.getRecordId() + "_" + System.currentTimeMillis();
        int feeCents = feeVo.getFeeCents() != null ? feeVo.getFeeCents() : 0;
        ParkingOrder order = orderService.createOrder(record, feeCents, idempotencyKey);

        // 零元订单直接完成
        if (ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            MiniPayResultVO vo = new MiniPayResultVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStatus("COMPLETED");
            vo.setPayableAmount(0);
            vo.setPayableAmountYuan("0.00");
            return R.ok(vo);
        }

        // 5. 标记支付中 + 创建模拟支付记录
        orderService.startPaying(order.getId(), ParkingOrder.PAY_CHANNEL_PYUN);
        mockPaymentService.preparePay(order);

        return buildPrepareResult(order);
    }

    /**
     * 模拟支付确认（替代真实的 wx.requestPayment 回调）。
     * <p>
     * 小程序端点击"确认支付"后调用此接口完成支付。
     * 不再需要真实的 wx.requestPayment 调用及 paySign 等参数。
     *
     * @param request 支付通知请求（仅需 orderId）
     * @return 处理结果
     */
    @PostMapping("/notify")
    @RequirePermission("miniapp:view")
    public R<?> payNotify(@Valid @RequestBody MiniPayNotifyRequest request) {
        ParkingOrder order = orderService.getById(request.getOrderId());
        if (order == null) {
            return R.fail(1003, "订单不存在");
        }

        // 幂等：已支付/已完成的订单直接返回成功
        if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            log.info("订单已处理，跳过通知: orderId={}", order.getId());
            return R.ok(Map.of("orderId", order.getId(), "status", "PAID"));
        }

        // 调用模拟支付确认
        boolean success = mockPaymentService.confirmPay(order.getId(), PAID_BY_MINIAPP);
        if (!success) {
            log.warn("模拟支付确认失败: orderId={} status={}", order.getId(), order.getStatus());
            return R.fail(4002, "支付失败，订单可能已过期");
        }

        // 重新查询订单获取最新状态
        order = orderService.getById(request.getOrderId());

        // 推送 WebSocket 通知到岗亭
        boothWebSocketPublisher.sendPaymentCompleted(
                order.getParkingLotId(),
                order.getId(),
                order.getOrderNo(),
                order.getPlateNumber(),
                order.getPaidAmount());

        // 创建支付成功消息通知（Phase 3 E3）
        try {
            Long currentWxUserId = wxUserService.getCurrentWxUserId();
            miniMessageService.createPaySuccessMessage(
                    currentWxUserId, order.getTenantId(),
                    order.getId(), order.getPlateNumber(), order.getPaidAmount());
        } catch (Exception e) {
            log.warn("支付消息通知创建失败（不影响主流程）: orderId={}", order.getId(), e);
        }

        log.info("小程序模拟支付完成: orderId={} orderNo={} plate={} amount={}",
                order.getId(), order.getOrderNo(), order.getPlateNumber(), order.getPaidAmount());

        return R.ok(Map.of("orderId", order.getId(), "status", "PAID"));
    }

    /**
     * 代理支付预览 — 查询代缴费用信息（Phase 3 E1）。
     * <p>
     * 不创建订单，不触发支付，仅查询车牌在场记录和费用。
     *
     * @param request 代缴请求（车牌号）
     * @return 费用预览信息
     */
    @PostMapping("/proxy-preview")
    @RequirePermission("miniapp:view")
    public R<Map<String, Object>> proxyPreview(@Valid @RequestBody ProxyPayRequest request) {
        String plate = request.getPlateNumber().toUpperCase().replaceAll("\\s+", "");
        if (plate.isEmpty()) {
            return R.fail(4002, "车牌号不能为空");
        }

        // 1. 查询车牌在场记录
        List<ParkingRecord> activeRecords = recordMapper.selectByPlates(List.of(plate));
        ParkingRecord record = null;
        for (ParkingRecord r : activeRecords) {
            if (ParkingRecord.STATUS_PARKING.equals(r.getStatus())) {
                record = r;
                break;
            }
        }
        if (record == null) {
            return R.fail(4002, "该车牌当前没有在场记录");
        }

        // 2. 检查是否已有已支付/已完成的订单
        QueryWrapper<ParkingOrder> paidQuery = new QueryWrapper<ParkingOrder>()
                .eq("parking_record_id", record.getId())
                .in("status", ParkingOrder.STATUS_PAID, ParkingOrder.STATUS_COMPLETED)
                .isNull("deleted_at")
                .last("LIMIT 1");
        ParkingOrder paidOrder = orderMapper.selectOne(paidQuery);
        if (paidOrder != null) {
            return R.fail(4002, "该车牌已支付，无需重复缴费");
        }

        // 3. 计算费用
        LocalDateTime now = LocalDateTime.now();
        int feeCents = billingEngine.calculateFee(record.getParkingLotId(), record.getEntryTime(), now);

        // 4. 构建结果
        ParkingLot lot = parkingLotMapper.selectById(record.getParkingLotId());
        long durationMinutes = java.time.Duration.between(record.getEntryTime(), now).toMinutes();

        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        result.put("plateNumber", plate);
        result.put("parkingLotName", lot != null ? lot.getName() : "");
        result.put("entryTime", record.getEntryTime() != null ? record.getEntryTime().toString() : "");
        result.put("durationMinutes", durationMinutes);
        result.put("feeCents", feeCents);
        result.put("feeYuan", formatYuan(feeCents));
        result.put("payable", feeCents > 0);
        return R.ok(result);
    }

    /**
     * 代理支付（代缴停车费）— Phase 3 E1。
     * <p>
     * 为任意车牌代缴停车费，不校验车牌归属（与正常支付的区别）。
     * 支付记录归实际支付人（代缴人）。
     * </p>
     * 流程：
     * <ol>
     *   <li>查询车牌当前在场记录</li>
     *   <li>计算费用</li>
     *   <li>创建停车订单</li>
     *   <li>确认支付（模拟支付）</li>
     *   <li>创建代缴记录</li>
     *   <li>推送 WebSocket 通知</li>
     * </ol>
     *
     * @param request 代缴请求（车牌号）
     * @return 代缴结果
     */
    @PostMapping("/proxy-pay")
    @RequirePermission("miniapp:view")
    public R<Map<String, Object>> proxyPay(@Valid @RequestBody ProxyPayRequest request) {
        String plate = request.getPlateNumber().toUpperCase().replaceAll("\\s+", "");
        if (plate.isEmpty()) {
            return R.fail(4002, "车牌号不能为空");
        }

        // 1. 获取代缴人信息
        Long payerId = wxUserService.getCurrentWxUserId();
        WxUser payer = wxUserMapper.selectById(payerId);
        String payerName = payer != null ? payer.getMaskedNickname() : "";

        // 2. 查询车牌当前在场记录（跨租户：小程序无 tenantId，按车牌查询）
        List<ParkingRecord> activeRecords = recordMapper.selectByPlates(List.of(plate));
        ParkingRecord record = null;
        for (ParkingRecord r : activeRecords) {
            if (ParkingRecord.STATUS_PARKING.equals(r.getStatus())) {
                record = r;
                break;
            }
        }
        if (record == null) {
            return R.fail(4002, "该车牌当前没有在场记录，无需缴费");
        }

        // 3. 检查是否已有已支付/已完成的订单
        QueryWrapper<ParkingOrder> orderQuery = new QueryWrapper<ParkingOrder>()
                .eq("parking_record_id", record.getId())
                .in("status", ParkingOrder.STATUS_PAID, ParkingOrder.STATUS_COMPLETED)
                .isNull("deleted_at")
                .last("LIMIT 1");
        ParkingOrder paidOrder = orderMapper.selectOne(orderQuery);
        if (paidOrder != null) {
            return R.fail(4002, "该车牌已支付，无需重复缴费");
        }

        // 4. 计算费用
        LocalDateTime now = LocalDateTime.now();
        int feeCents = billingEngine.calculateFee(record.getParkingLotId(), record.getEntryTime(), now);

        // 零元订单无需代缴
        if (feeCents <= 0) {
            return R.fail(4002, "当前仍在免费时段内，无需缴费");
        }

        // 5. 创建订单（幂等键使用 PROXY_ + recordId + 时间戳）
        String idempotencyKey = "PROXY_" + record.getId() + "_" + System.currentTimeMillis();
        ParkingOrder order = orderService.createOrder(record, feeCents, idempotencyKey);

        // 零元订单（理论不会发生，但 createOrder 内部可能处理为 COMPLETED）
        if (ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("status", "COMPLETED");
            result.put("amountCents", 0);
            result.put("plateNumber", plate);
            return R.ok(result);
        }

        // 6. 确认支付（模拟支付：直接完成）
        String paidBy = PAID_BY_PROXY_PREFIX + payerId;
        boolean paySuccess = mockPaymentService.confirmPay(order.getId(), paidBy);
        if (!paySuccess) {
            log.warn("代缴支付失败: orderId={} plate={} payerId={}", order.getId(), plate, payerId);
            return R.fail(4002, "代缴支付失败，订单可能已过期");
        }

        // 重新查询订单获取最新状态
        order = orderService.getById(order.getId());

        // 7. 创建代缴记录
        ProxyPayRecord proxyRecord = new ProxyPayRecord();
        proxyRecord.setTenantId(record.getTenantId());
        proxyRecord.setParkingLotId(record.getParkingLotId());
        proxyRecord.setOrderId(order.getId());
        proxyRecord.setRecordId(record.getId());
        proxyRecord.setPlateNumber(plate);
        proxyRecord.setPayerId(payerId);
        proxyRecord.setPayerName(payerName);
        proxyRecord.setOwnerId(null);
        proxyRecord.setOwnerName(null);
        proxyRecord.setAmountCents(order.getPayableAmount());
        proxyRecord.setStatus(ProxyPayRecord.STATUS_COMPLETED);
        proxyRecord.setPaySerial(order.getPaySerial());
        proxyRecord.setRemark(request.getRemark());
        proxyRecord.setCreatedAt(LocalDateTime.now());
        proxyRecord.setUpdatedAt(LocalDateTime.now());
        proxyPayRecordMapper.insert(proxyRecord);

        // 8. 推送 WebSocket 通知到岗亭
        boothWebSocketPublisher.sendPaymentCompleted(
                order.getParkingLotId(),
                order.getId(),
                order.getOrderNo(),
                order.getPlateNumber(),
                order.getPaidAmount());

        log.info("代缴完成: orderId={} plate={} payerId={} payer={} amount={}",
                order.getId(), plate, payerId, payerName, order.getPayableAmount());

        // 9. 创建支付成功消息通知给代缴人（Phase 3 E3）
        try {
            miniMessageService.createPaySuccessMessage(
                    payerId, record.getTenantId(),
                    order.getId(), plate, order.getPayableAmount());
        } catch (Exception e) {
            log.warn("代缴消息通知创建失败（不影响主流程）: orderId={}", order.getId(), e);
        }

        // 10. 构建结果
        ParkingLot lot = parkingLotMapper.selectById(record.getParkingLotId());
        String lotName = lot != null ? lot.getName() : "";

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("status", "PAID");
        result.put("amountCents", order.getPayableAmount());
        result.put("amountYuan", formatYuan(order.getPayableAmount()));
        result.put("plateNumber", plate);
        result.put("parkingLotName", lotName);
        result.put("entryTime", record.getEntryTime() != null ? record.getEntryTime().toString() : "");
        return R.ok(result);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建预支付结果（模拟支付版本）。
     * 返回简单的订单信息，无需 P云支付参数。
     */
    private R<MiniPayResultVO> buildPrepareResult(ParkingOrder order) {
        MiniPayResultVO vo = new MiniPayResultVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPayChannel(ParkingOrder.PAY_CHANNEL_PYUN);
        vo.setStatus("PAYING");
        vo.setPayableAmount(order.getPayableAmount());
        vo.setPayableAmountYuan(formatYuan(order.getPayableAmount()));

        // 模拟支付不需要 prepayParams（无需 wx.requestPayment）
        // 前端直接调用 /notify 完成支付即可

        return R.ok(vo);
    }

    private String formatYuan(int cents) {
        return String.format("%.2f", cents / 100.0);
    }
}
