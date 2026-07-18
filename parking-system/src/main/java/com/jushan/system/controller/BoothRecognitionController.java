package com.jushan.system.controller;

import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.service.RecognitionCorrectionService;
import com.jushan.system.ws.BoothWebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 岗亭端车牌校正接口（BOOTH-005 任务包 4-1）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/booth/recognition")
public class BoothRecognitionController {

    private static final Logger log = LoggerFactory.getLogger(BoothRecognitionController.class);

    private final RecognitionCorrectionService correctionService;
    private final BoothWebSocketPublisher wsPublisher;

    public BoothRecognitionController(RecognitionCorrectionService correctionService,
                                       BoothWebSocketPublisher wsPublisher) {
        this.correctionService = correctionService;
        this.wsPublisher = wsPublisher;
    }

    /**
     * 校正车牌号。
     *
     * @param logId   识别事件日志 ID
     * @param request 校正请求体
     * @return 成功响应
     */
    @PostMapping("/{logId}/correct")
    @RequirePermission("booth:monitor")
    @BusinessLog(value = "车牌校正", module = "recognition", operationType = "UPDATE",
            operationObject = "识别事件", objectIdExpression = "#logId")
    public R<Void> correctPlate(@PathVariable Long logId,
                                 @RequestBody CorrectPlateRequest request) {
        String correctedPlate = request.getCorrectedPlate();
        if (correctedPlate == null || correctedPlate.isBlank()) {
            return R.fail(CommonErrorCode.PARAM_ERROR.getCode(), "校正车牌号不能为空");
        }

        Long correctorId = TenantContext.getUserId();

        RecognitionEventLog updatedEvent = correctionService.correctPlate(logId, correctedPlate, correctorId);

        // 校正成功后通过 WebSocket 推送更新到岗亭前端
        try {
            wsPublisher.sendRecognitionEvent(updatedEvent.getParkingLotId(), updatedEvent);
        } catch (Exception e) {
            log.warn("车牌校正 WebSocket 推送失败（不影响主业务）: logId={}", logId, e);
        }

        return R.ok();
    }

    /**
     * 校正请求 DTO。
     */
    public static class CorrectPlateRequest {
        private String correctedPlate;

        public String getCorrectedPlate() { return correctedPlate; }
        public void setCorrectedPlate(String correctedPlate) { this.correctedPlate = correctedPlate; }
    }
}
