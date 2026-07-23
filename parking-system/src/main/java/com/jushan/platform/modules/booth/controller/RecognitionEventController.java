package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.infra.security.RequireRole;
import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.service.RecognitionEventService;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import com.jushan.system.client.dto.CaptureResultDTO;
import com.jushan.system.client.dto.CommandResultDTO;
import com.jushan.system.service.DeviceService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 识别事件处理控制器。
 * <p>
 * 处理车牌识别事件，包含车辆判定、余位校验、费用计算、开闸决策。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/booth/recognition")
@RequireRole({"platform", "tenant", "booth"}) // GB-02~05: 三种角色均可使用岗亭工作区（V1.4）
public class RecognitionEventController {

    private final RecognitionEventService recognitionEventService;
    private final DeviceService deviceService;

    public RecognitionEventController(RecognitionEventService recognitionEventService,
                                       DeviceService deviceService) {
        this.recognitionEventService = recognitionEventService;
        this.deviceService = deviceService;
    }

    /**
     * 处理车牌识别事件。
     */
    @PostMapping("/handle")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> handleEvent(@Valid @RequestBody RecognitionEventCmd cmd) {
        RecognitionResultVO result = recognitionEventService.handleEvent(cmd);
        log.info("识别事件处理: plate={}, direction={}, allowPass={}, exception={}",
                cmd.getPlateNumber(), cmd.getDirection(), result.getAllowPass(), result.getException());
        return R.ok(result);
    }

    /**
     * 人工开闸（单通道）。
     * <p>
     * 支持传入 isCharge / feeCents / plateNumber 用于记录手工计费或免费放行的审计信息。
     * 向后兼容：所有新参数均可选（有默认值），已有调用方（如批量开闸）无需修改。
     */
    @PostMapping("/manual-open-gate")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> manualOpenGate(
            @RequestParam Long laneId,
            @RequestParam String reason,
            @RequestParam(defaultValue = "false") boolean isCharge,
            @RequestParam(defaultValue = "0") Integer feeCents,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String entryImage) {
        Long operatorId = com.jushan.common.auth.TenantContext.getUserId();
        RecognitionResultVO result = recognitionEventService.manualOpenGate(
                laneId, operatorId, reason, isCharge, feeCents, plateNumber, entryImage);
        log.info("人工开闸: laneId={}, operatorId={}, reason={}, isCharge={}, feeCents={}, plateNumber={}, entryImage={}",
                laneId, operatorId, reason, isCharge, feeCents, plateNumber, entryImage);
        return R.ok(result);
    }

    /**
     * 手动抓拍指定车道相机。
     * <p>
     * 选择车道主相机（或唯一相机）触发抓拍，返回图片 URL。
     *
     * @param laneId 通道 ID
     * @return 抓拍结果（含图片 URL）
     */
    @PostMapping("/manual-capture")
    @RequirePermission("booth:operate")
    public R<CaptureResultDTO> manualCapture(@RequestParam Long laneId) {
        CaptureResultDTO result = recognitionEventService.captureImage(laneId);
        log.info("手动抓拍: laneId={}, success={}, imageUrl={}", laneId, result.isSuccessful(), result.getImageUrl());
        return R.ok(result);
    }

    /**
     * 批量多通道开闸（Phase 2 D5）。
     * <p>
     * 支持一次性对多个设备发起开闸命令。
     * 循环调用 DeviceService.openGate()，记录每条操作日志。
     *
     * @param body 请求体：{ deviceIds: [], reason, isCharge, amount }
     */
    @PostMapping("/manual-open-gate-batch")
    @RequirePermission("booth:operate")
    public R<Map<String, Object>> manualOpenGateBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> deviceIdObjs = body.get("deviceIds") != null
                ? (List<Object>) body.get("deviceIds") : List.of();
        String reason = body.get("reason") != null ? body.get("reason").toString().trim() : "批量开闸";
        boolean isCharge = body.get("isCharge") != null && Boolean.TRUE.equals(body.get("isCharge"));
        Integer amount = body.get("amount") != null ? ((Number) body.get("amount")).intValue() : null;

        if (deviceIdObjs.isEmpty()) {
            return R.fail(com.jushan.common.CommonErrorCode.PARAM_ERROR.getCode(), "deviceIds 不能为空");
        }

        List<Map<String, Object>> successList = new ArrayList<>();
        List<Map<String, Object>> failedList = new ArrayList<>();

        for (Object obj : deviceIdObjs) {
            Long deviceId = ((Number) obj).longValue();
            try {
                CommandResultDTO result = deviceService.openGate(deviceId, reason);
                Map<String, Object> item = new HashMap<>();
                item.put("deviceId", deviceId);
                item.put("success", result.isSuccessful());
                item.put("message", result.getMessage());

                if (result.isSuccessful()) {
                    successList.add(item);
                } else {
                    failedList.add(item);
                }

                log.info("批量开闸: deviceId={}, success={}, message={}",
                        deviceId, result.isSuccessful(), result.getMessage());
            } catch (Exception e) {
                Map<String, Object> item = new HashMap<>();
                item.put("deviceId", deviceId);
                item.put("success", false);
                item.put("message", e.getMessage());
                failedList.add(item);

                log.warn("批量开闸异常: deviceId={}, error={}", deviceId, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", successList);
        result.put("failed", failedList);
        result.put("total", deviceIdObjs.size());
        result.put("successCount", successList.size());
        result.put("failedCount", failedList.size());

        log.info("批量开闸完成: total={}, success={}, failed={}",
                deviceIdObjs.size(), successList.size(), failedList.size());

        return R.ok(result);
    }

    /**
     * 人工关闸。
     */
    @PostMapping("/manual-close-gate")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> manualCloseGate(
            @RequestParam Long laneId,
            @RequestParam String reason) {
        Long operatorId = com.jushan.common.auth.TenantContext.getUserId();
        RecognitionResultVO result = recognitionEventService.manualCloseGate(laneId, operatorId, reason);
        log.info("人工关闸: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);
        return R.ok(result);
    }

    /**
     * 常开（锁定道闸，继电器强制吸合保持开启）。
     */
    @PostMapping("/manual-lock-gate")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> manualLockGate(
            @RequestParam Long laneId,
            @RequestParam String reason) {
        Long operatorId = com.jushan.common.auth.TenantContext.getUserId();
        RecognitionResultVO result = recognitionEventService.manualLockGate(laneId, operatorId, reason);
        log.info("常开（锁定道闸）: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);
        return R.ok(result);
    }

    /**
     * 取消常开（解除道闸锁定并关闸，恢复常规模式）。
     */
    @PostMapping("/manual-unlock-gate")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> manualUnlockGate(
            @RequestParam Long laneId,
            @RequestParam String reason) {
        Long operatorId = com.jushan.common.auth.TenantContext.getUserId();
        RecognitionResultVO result = recognitionEventService.manualUnlockGate(laneId, operatorId, reason);
        log.info("取消常开（解除道闸锁定）: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);
        return R.ok(result);
    }
}
