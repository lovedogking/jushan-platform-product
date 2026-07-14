package com.jushan.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.PayMerchantConfig;
import com.jushan.system.entity.PayOrder;
import com.jushan.system.mapper.PayMerchantConfigMapper;
import com.jushan.system.service.ParkingOrderService;
import com.jushan.system.service.PyunPaymentClient;
import com.jushan.system.mapper.PayOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * P云支付回调 Controller（Sprint 8）。
 * <p>
 * 处理 P云支付结果异步通知（Form 表单 POST）。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>回调必须验签</li>
 *   <li>校验 pay_order、merchant、value 和订单匹配</li>
 *   <li>幂等：同一 pay_serial 只处理一次</li>
 *   <li>重复回调返回 1001 成功</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/pay")
public class PyunNotifyController {

    private static final Logger log = LoggerFactory.getLogger(PyunNotifyController.class);

    private final PyunPaymentClient pyunClient;
    private final ParkingOrderService orderService;
    private final PayOrderMapper payOrderMapper;
    private final PayMerchantConfigMapper merchantConfigMapper;
    private final ObjectMapper objectMapper;

    public PyunNotifyController(PyunPaymentClient pyunClient,
                                 ParkingOrderService orderService,
                                 PayOrderMapper payOrderMapper,
                                 PayMerchantConfigMapper merchantConfigMapper,
                                 ObjectMapper objectMapper) {
        this.pyunClient = pyunClient;
        this.orderService = orderService;
        this.payOrderMapper = payOrderMapper;
        this.merchantConfigMapper = merchantConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * P云支付结果异步通知。
     */
    @PostMapping("/notify")
    public Map<String, String> handleNotify(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        log.info("P云支付回调: params={}", maskSensitive(params));

        String payOrderNo = params.get("pay_order");
        String merchantNo = params.get("merchant");
        String paySerial = params.get("pay_serial");
        String valueStr = params.get("value");
        String statusStr = params.get("status");
        String sign = params.get("sign");

        if (payOrderNo == null || merchantNo == null || paySerial == null || sign == null) {
            log.warn("P云回调参数缺失");
            return errorResponse("参数缺失");
        }

        // 查找订单
        ParkingOrder order = orderService.getByOrderNo(payOrderNo);
        if (order == null) {
            log.warn("P云回调订单不存在: pay_order={}", payOrderNo);
            return errorResponse("订单不存在");
        }

        // 查找商户配置
        PayMerchantConfig config = merchantConfigMapper.selectActiveByParkingLot(order.getTenantId(), order.getParkingLotId());
        if (config == null) {
            log.warn("P云回调商户配置不存在: parkingLotId={}", order.getParkingLotId());
            return errorResponse("商户配置不存在");
        }

        // 校验商户号
        if (!config.getMerchantNo().equals(merchantNo)) {
            log.warn("P云回调商户号不匹配: expected={} actual={}", config.getMerchantNo(), merchantNo);
            return errorResponse("商户号不匹配");
        }

        // 验签
        Map<String, String> signParams = new HashMap<>(params);
        boolean verified = pyunClient.verifyNotifySign(signParams, config.getAppSecret());
        if (!verified) {
            log.warn("P云回调验签失败: pay_order={}", payOrderNo);
            return errorResponse("签名验证失败");
        }

        // 幂等检查
        PayOrder existingPay = payOrderMapper.selectByPaySerial(paySerial);
        if (existingPay != null && PayOrder.STATUS_SUCCESS.equals(existingPay.getStatus())) {
            log.info("P云回调幂等返回: pay_serial={}", paySerial);
            return successResponse();
        }

        // 校验金额
        int valueCents = parseIntSafe(valueStr);
        if (valueCents != order.getPayableAmount()) {
            log.warn("P云回调金额不匹配: expected={} actual={}", order.getPayableAmount(), valueCents);
            return errorResponse("金额不匹配");
        }

        // 处理支付结果
        int status = parseIntSafe(statusStr);
        if (status == 1) {
            // 支付成功
            boolean marked = orderService.markPaid(order.getId(), paySerial, valueCents);
            if (marked) {
                // 记录支付流水
                recordPayOrder(order, paySerial, valueCents, params);
                log.info("P云回调支付成功: orderId={} pay_serial={} value={}", order.getId(), paySerial, valueCents);
                return successResponse();
            } else {
                log.warn("P云回调更新订单状态失败: orderId={}", order.getId());
                return errorResponse("订单状态更新失败");
            }
        } else if (status == -1) {
            // 支付失败
            orderService.markPayFailed(order.getId());
            log.info("P云回调支付失败: orderId={} pay_serial={}", order.getId(), paySerial);
            return successResponse(); // 返回成功让 P云停止通知
        } else {
            // 支付中，暂不处理
            log.info("P云回调支付中: orderId={} pay_serial={} status={}", order.getId(), paySerial, status);
            return successResponse();
        }
    }

    private void recordPayOrder(ParkingOrder order, String paySerial, int valueCents, Map<String, String> params) {
        PayOrder pay = new PayOrder();
        pay.setTenantId(order.getTenantId());
        pay.setParkingLotId(order.getParkingLotId());
        pay.setOrderId(order.getId());
        pay.setPayOrderNo(order.getOrderNo());
        pay.setPaySerial(paySerial);
        pay.setPayChannel(ParkingOrder.PAY_CHANNEL_PYUN);
        pay.setPayAmount(valueCents);
        pay.setStatus(PayOrder.STATUS_SUCCESS);
        pay.setPayerOpenId(params.get("payer"));
        pay.setTradeNo(params.get("trade_no"));
        try {
            long tradeTimeMs = Long.parseLong(params.getOrDefault("trade_time", "0"));
            pay.setTradeTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(tradeTimeMs), ZoneId.systemDefault()));
        } catch (Exception e) {
            pay.setTradeTime(LocalDateTime.now());
        }
        try {
            pay.setNotifyRaw(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            pay.setNotifyRaw("{}");
        }
        pay.setFeeCents(parseIntSafe(params.getOrDefault("fee", "0")));
        pay.setCreatedAt(LocalDateTime.now());
        pay.setUpdatedAt(LocalDateTime.now());
        payOrderMapper.insert(pay);
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }

    private Map<String, String> maskSensitive(Map<String, String> params) {
        Map<String, String> masked = new HashMap<>(params);
        if (masked.containsKey("sign")) {
            masked.put("sign", "***");
        }
        return masked;
    }

    private Map<String, String> successResponse() {
        Map<String, String> resp = new HashMap<>();
        resp.put("code", "1001");
        resp.put("message", "通知成功");
        return resp;
    }

    private Map<String, String> errorResponse(String message) {
        Map<String, String> resp = new HashMap<>();
        resp.put("code", "1500");
        resp.put("message", message);
        return resp;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
