package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.booth.dto.RecognitionEventCmd;
import com.jushan.platform.modules.booth.service.RecognitionEventService;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
public class RecognitionEventController {

    private final RecognitionEventService recognitionEventService;

    public RecognitionEventController(RecognitionEventService recognitionEventService) {
        this.recognitionEventService = recognitionEventService;
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
     * 人工开闸（预留/mock）。
     */
    @PostMapping("/manual-open-gate")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> manualOpenGate(
            @RequestParam Long laneId,
            @RequestParam String reason) {
        Long operatorId = com.jushan.common.auth.TenantContext.getUserId();
        RecognitionResultVO result = recognitionEventService.manualOpenGate(laneId, operatorId, reason);
        log.info("人工开闸: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);
        return R.ok(result);
    }
}
