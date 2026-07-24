package com.jushan.platform.modules.parking.controller;

import com.jushan.common.R;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.entity.PayMerchantConfig;
import com.jushan.platform.modules.parking.mapper.ParkingLotMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import com.jushan.platform.modules.parking.mapper.PayMerchantConfigMapper;
import com.jushan.platform.modules.parking.service.BillingEngine;
import com.jushan.platform.modules.parking.service.ParkingOrderService;
import com.jushan.platform.modules.vehicle.service.VehicleRenewalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * P云 PP 前端接口 Controller（Sprint 8）。
 * <p>
 * <strong>已废弃（Phase 0 S0-3）</strong>：系统已切换为模拟支付模式，
 * P云开放平台不再调用。此 Controller 保留仅供参考。
 * <p>
 * P云开放平台调用停车场系统的接口：
 * <ul>
 *   <li>POST /api/v1/pyun/billing —— 获取临停缴费订单（service.parking.payment.billing）</li>
 *   <li>POST /api/v1/pyun/payment-result —— 同步临停缴费通知（service.parking.payment.result）</li>
 *   <li>POST /api/v1/pyun/vehicle-info —— 车辆信息查询（service.parking.vehicle.info）</li>
 *   <li>POST /api/v1/pyun/renewal-notify —— 车辆续费结果通知（service.parking.renewal.notify）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @deprecated 系统已切换为模拟支付（MockPaymentService）。此类不会在生产路径中被调用。
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/pyun")
public class PyunPpFrontController {

    private static final Logger log = LoggerFactory.getLogger(PyunPpFrontController.class);

    private final ParkingRecordMapper parkingRecordMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final PayMerchantConfigMapper merchantConfigMapper;
    private final ParkingOrderService orderService;
    private final BillingEngine billingEngine;
    private final VehicleRenewalService renewalService;

    public PyunPpFrontController(ParkingRecordMapper parkingRecordMapper,
                                  ParkingLotMapper parkingLotMapper,
                                  PayMerchantConfigMapper merchantConfigMapper,
                                  ParkingOrderService orderService,
                                  BillingEngine billingEngine,
                                  VehicleRenewalService renewalService) {
        this.parkingRecordMapper = parkingRecordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.merchantConfigMapper = merchantConfigMapper;
        this.orderService = orderService;
        this.billingEngine = billingEngine;
        this.renewalService = renewalService;
    }

    /**
     * 获取临停缴费订单（P云调用）。
     * <p>
     * 服务名: service.parking.payment.billing
     */
    @PostMapping("/billing")
    public R<?> getBillingOrder(@RequestBody Map<String, Object> request) {
        String parkUuid = (String) request.get("park_uuid");
        String plate = (String) request.get("plate");
        String passport = (String) request.get("passport");

        log.info("P云获取临停订单: parkUuid={} plate={}", parkUuid, plate);

        // 查找停车场配置
        PayMerchantConfig config = findByParkUuid(parkUuid);
        if (config == null) {
            return R.fail(1500, "停车场未接入P云");
        }

        // 按车牌查询在场记录
        ParkingRecord record = null;
        if (plate != null && !plate.isEmpty()) {
            record = findActiveRecord(config.getParkingLotId(), plate.toUpperCase());
        }

        if (record == null) {
            return R.ok(Map.of(
                    "result_code", "1002",
                    "message", "未查询到停车信息"
            ));
        }

        // 计算费用
        int feeCents = billingEngine.calculateFee(config.getParkingLotId(), record.getEntryTime(), LocalDateTime.now());
        long parkingTimeSeconds = java.time.Duration.between(record.getEntryTime(), LocalDateTime.now()).getSeconds();

        // 生成停车流水和订单号
        String parkingSerial = String.valueOf(record.getId());
        String parkingOrder = "P" + config.getParkingLotId() + System.currentTimeMillis();

        Map<String, Object> result = new HashMap<>();
        result.put("result_code", "1001");
        result.put("message", "订单获取成功");
        result.put("plate", record.getStandardizedPlate());
        result.put("parking_serial", parkingSerial);
        result.put("parking_order", parkingOrder);
        result.put("enter_time", record.getEntryTime().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        result.put("parking_time", (int) parkingTimeSeconds);
        result.put("total_value", feeCents);
        result.put("free_value", 0);
        result.put("paid_value", 0);
        result.put("pay_value", feeCents);
        result.put("car_type", 1);
        result.put("car_desc", "临停车辆");

        return R.ok(result);
    }

    /**
     * 同步临停缴费通知（P云调用）。
     * <p>
     * 服务名: service.parking.payment.result
     */
    @PostMapping("/payment-result")
    public R<?> syncPaymentResult(@RequestBody Map<String, Object> request) {
        String parkUuid = (String) request.get("park_uuid");
        String parkingOrder = (String) request.get("parking_order");
        String parkingSerial = (String) request.get("parking_serial");
        String paySerial = (String) request.get("pay_serial");
        int value = parseIntSafe(request.get("value"));
        int payValue = parseIntSafe(request.get("pay_value"));

        log.info("P云缴费通知: parkUuid={} parkingOrder={} paySerial={} value={}",
                parkUuid, parkingOrder, paySerial, value);

        PayMerchantConfig config = findByParkUuid(parkUuid);
        if (config == null) {
            return R.ok(Map.of("result_code", "1001", "message", "通知成功"));
        }

        // 查找关联订单并更新状态
        if (parkingSerial != null) {
            try {
                Long recordId = Long.parseLong(parkingSerial);
                ParkingRecord record = parkingRecordMapper.selectById(recordId);
                if (record != null) {
                    // 标记已支付（实际业务中应关联 ParkingOrder）
                    log.info("P云缴费通知已处理: recordId={} paySerial={}", recordId, paySerial);
                }
            } catch (Exception e) {
                log.warn("P云缴费通知处理异常", e);
            }
        }

        return R.ok(Map.of("result_code", "1001", "message", "通知成功"));
    }

    /**
     * 车辆信息查询（P云调用）。
     * <p>
     * 服务名: service.parking.vehicle.info
     */
    @PostMapping("/vehicle-info")
    public R<?> getVehicleInfo(@RequestBody Map<String, Object> request) {
        String parkUuid = (String) request.get("park_uuid");
        String plate = (String) request.get("plate");

        log.info("P云车辆信息查询: parkUuid={} plate={}", parkUuid, plate);

        // 一期预留：返回基础信息
        return R.ok(Map.of(
                "result_code", "1001",
                "message", "查询成功",
                "plate", plate != null ? plate : "",
                "car_type", 1,
                "car_desc", "临停车辆"
        ));
    }

    /**
     * 车辆续费结果通知（P云调用）。
     * <p>
     * 服务名: service.parking.renewal.notify
     */
    @PostMapping("/renewal-notify")
    public R<?> renewalNotify(@RequestBody Map<String, Object> request) {
        String parkUuid = (String) request.get("park_uuid");
        String plate = (String) request.get("plate");
        int days = parseIntSafe(request.get("days"));
        String paySerial = (String) request.get("pay_serial");

        log.info("P云续费通知: parkUuid={} plate={} days={} paySerial={}", parkUuid, plate, days, paySerial);

        // 解析停车场配置，得到可信租户与停车场
        PayMerchantConfig config = findByParkUuid(parkUuid);
        if (config == null) {
            return R.ok(Map.of("result_code", "1001", "message", "通知成功"));
        }

        // 按车牌匹配最新待生效的月卡续费订单并触发生效（找不到则忽略，P云要求返回成功）
        try {
            String serial = (paySerial != null && !paySerial.isEmpty())
                    ? paySerial : ("PYUN-RN-" + System.currentTimeMillis());
            renewalService.applyRenewalByPlate(config.getTenantId(), config.getParkingLotId(), plate, serial);
        } catch (Exception e) {
            log.error("P云续费通知处理异常: plate={}", plate, e);
        }
        return R.ok(Map.of("result_code", "1001", "message", "通知成功"));
    }

    // ==================== 内部方法 ====================

    private PayMerchantConfig findByParkUuid(String parkUuid) {
        if (parkUuid == null || parkUuid.isEmpty()) {
            return null;
        }
        // 简化为查询所有配置匹配 parkUuid
        // 实际应使用索引查询
        return merchantConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayMerchantConfig>()
                        .eq("park_uuid", parkUuid)
                        .eq("status", "ACTIVE")
                        .isNull("deleted_at")
                        .last("LIMIT 1")).stream().findFirst().orElse(null);
    }

    private ParkingRecord findActiveRecord(Long parkingLotId, String plate) {
        return parkingRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ParkingRecord>()
                        .eq("parking_lot_id", parkingLotId)
                        .eq("standardized_plate", plate)
                        .eq("status", "PARKING")
                        .orderByDesc("entry_time")
                        .last("LIMIT 1")).stream().findFirst().orElse(null);
    }

    private int parseIntSafe(Object value) {
        if (value == null) return 0;
        try {
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
