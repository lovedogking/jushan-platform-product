package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.entity.MockPaymentConfig;
import com.jushan.platform.modules.miniapp.entity.MockPaymentRecord;
import com.jushan.platform.modules.parking.service.MockPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 模拟支付管理 Controller（Phase 0 S0-3）。
 * <p>
 * 提供运营端模拟支付的配置管理和流水查询能力。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>所有接口需要 {@code payment:manage} 权限</li>
 *   <li>配置修改自动写入操作日志</li>
 *   <li>手动标记支付需要二次确认</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/mock-payment")
public class MockPaymentController {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentController.class);

    private final MockPaymentService mockPaymentService;

    public MockPaymentController(MockPaymentService mockPaymentService) {
        this.mockPaymentService = mockPaymentService;
    }

    /**
     * 查询车场模拟支付配置。
     * GET /api/v1/mock-payment/config/{parkingLotId}
     */
    @GetMapping("/config/{parkingLotId}")
    @RequirePermission("payment:manage")
    public R<MockPaymentConfig> getConfig(@PathVariable Long parkingLotId) {
        MockPaymentConfig config = mockPaymentService.getConfig(parkingLotId);
        if (config == null) {
            return R.ok(new MockPaymentConfig()); // 返回空配置，前端初始化为默认值
        }
        return R.ok(config);
    }

    /**
     * 更新车场模拟支付配置。
     * PUT /api/v1/mock-payment/config/{parkingLotId}
     */
    @PutMapping("/config/{parkingLotId}")
    @RequirePermission("payment:manage")
    public R<MockPaymentConfig> updateConfig(@PathVariable Long parkingLotId,
                                              @RequestBody Map<String, Object> body) {
        Integer timeoutMinutes = body.get("timeoutMinutes") != null
                ? ((Number) body.get("timeoutMinutes")).intValue() : null;
        Boolean enabled = body.get("enabled") != null
                ? Boolean.valueOf(body.get("enabled").toString()) : null;

        Long tenantId = TenantContext.requireTenantId();
        MockPaymentConfig config = mockPaymentService.updateConfig(parkingLotId, tenantId, timeoutMinutes, enabled);
        log.info("模拟支付配置已更新: parkingLotId={} timeoutMinutes={} enabled={}",
                parkingLotId, config.getTimeoutMinutes(), config.getEnabled());
        return R.ok(config);
    }

    /**
     * 查询模拟支付流水。
     * GET /api/v1/mock-payment/records?parkingLotId=&plateNumber=&status=&page=1&size=20
     */
    @GetMapping("/records")
    @RequirePermission("payment:manage")
    public R<IPage<MockPaymentRecord>> queryRecords(
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<MockPaymentRecord> result = mockPaymentService.queryRecords(
                parkingLotId, plateNumber, status, page, size);
        return R.ok(result);
    }

    /**
     * 手动标记支付（运营端）。
     * POST /api/v1/mock-payment/{orderId}/manual-paid
     */
    @PostMapping("/{orderId}/manual-paid")
    @RequirePermission("payment:manage")
    public R<String> manualMarkPaid(@PathVariable Long orderId) {
        String operatorId = String.valueOf(TenantContext.requireUserId());
        boolean success = mockPaymentService.manualMarkPaid(orderId, operatorId);
        if (success) {
            log.info("手动标记支付成功: orderId={} operatorId={}", orderId, operatorId);
            return R.ok("已标记为已支付");
        } else {
            return R.fail(4002, "标记失败，订单不存在或状态异常");
        }
    }
}
