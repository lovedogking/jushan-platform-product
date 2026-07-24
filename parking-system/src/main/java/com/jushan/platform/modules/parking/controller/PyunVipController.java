package com.jushan.platform.modules.parking.controller;

import com.jushan.common.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P云无感支付接口 Controller（Sprint 8 预留）。
 * <p>
 * P云主动调用停车场系统的无感支付相关接口：
 * <ul>
 *   <li>POST /api/v1/pyun/vip —— 无感停车状态同步（service.parking.vip）</li>
 * </ul>
 * <p>
 * 一期预留：接口结构完整，返回 mock 数据。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/pyun")
public class PyunVipController {

    private static final Logger log = LoggerFactory.getLogger(PyunVipController.class);

    /**
     * 无感停车状态同步（P云调用）。
     * <p>
     * 当用户满足无感支付条件时，P云主动下发设置车辆自动支付权限。
     *
     * @param request 请求参数
     * @return 处理结果
     */
    @PostMapping("/vip")
    public R<?> syncVipStatus(@RequestBody Map<String, Object> request) {
        String parkUuid = (String) request.get("park_uuid");
        String plate = (String) request.get("plate");
        Integer credits = (Integer) request.get("credits");
        String parkingSerial = (String) request.get("parking_serial");
        Integer locking = (Integer) request.get("locking");

        log.info("P云无感状态同步: parkUuid={} plate={} credits={} locking={}",
                parkUuid, plate, credits, locking);

        // 一期预留：记录无感状态，实际扣款逻辑在出场时处理
        boolean enabled = credits != null && credits > 0;
        boolean locked = locking != null && locking == 1;

        log.info("P云无感状态已记录: parkingSerial={} enabled={} locked={}",
                parkingSerial, enabled, locked);

        return R.ok(Map.of(
                "result_code", "1001",
                "message", "接口处理成功",
                "service", "service.parking.vip",
                "version", "1.0"
        ));
    }
}
