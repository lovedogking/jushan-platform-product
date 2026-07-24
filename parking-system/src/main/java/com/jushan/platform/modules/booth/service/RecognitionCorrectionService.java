package com.jushan.platform.modules.booth.service;
import com.jushan.platform.modules.parking.service.ParkingLotScopeResolver;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.platform.modules.booth.entity.RecognitionEventLog;
import com.jushan.platform.modules.booth.mapper.RecognitionEventLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 车牌校正服务（BOOTH-005 任务包 4-1）。
 * <p>
 * 岗亭管理员可对已处理的识别事件手动校正车牌号。
 * 校正记录永久保留不可变，不修改已生成的 parking_record。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Service
public class RecognitionCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionCorrectionService.class);

    /** 校正类型固定值 */
    private static final String CORRECTION_TYPE_MANUAL = "MANUAL_CORRECTION";

    /** 最小车牌号长度 */
    private static final int PLATE_MIN_LENGTH = 2;

    /** 最大车牌号长度 */
    private static final int PLATE_MAX_LENGTH = 20;

    /** 可校正的状态 */
    private static final String ALLOWED_STATUS = "PROCESSED";

    private final RecognitionEventLogMapper logMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public RecognitionCorrectionService(RecognitionEventLogMapper logMapper,
                                         ParkingLotScopeResolver scopeResolver) {
        this.logMapper = logMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 执行车牌校正。
     *
     * @param logId          识别事件日志 ID
     * @param correctedPlate 校正后车牌号
     * @param correctorId    校正人用户 ID
     * @return 更新后的事件日志实体（用于 WebSocket 推送）
     * @throws BusinessException 校验失败时抛出
     */
    public RecognitionEventLog correctPlate(Long logId, String correctedPlate, Long correctorId) {
        // 1. 查询事件日志
        RecognitionEventLog event = logMapper.selectById(logId);
        if (event == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "识别事件不存在");
        }

        // 2. 状态校验：仅 PROCESSED 可校正
        if (!ALLOWED_STATUS.equals(event.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅已处理状态的识别事件可校正，当前状态: " + event.getStatus());
        }

        // 3. 车牌号格式校验
        String trimmed = correctedPlate.trim();
        if (trimmed.length() < PLATE_MIN_LENGTH || trimmed.length() > PLATE_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "校正车牌号长度需在" + PLATE_MIN_LENGTH + "-" + PLATE_MAX_LENGTH + "字符之间");
        }

        // 4. 有效性校验：校正值与原值相同视为无效
        if (trimmed.equals(event.getPlateNumber())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "校正车牌与原识别车牌相同");
        }

        // 5. 不可变约束：已校正记录拒绝再次校正
        if (event.getCorrectedPlate() != null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该识别事件已校正，不可重复校正");
        }

        // 6. 数据范围校验
        scopeResolver.validateAccess(event.getParkingLotId());

        // 7. 更新字段
        event.setCorrectedPlate(trimmed);
        event.setCorrectionType(CORRECTION_TYPE_MANUAL);
        event.setCorrectedAt(LocalDateTime.now());
        event.setCorrectorId(correctorId);

        logMapper.updateById(event);

        log.info("车牌校正成功: logId={}, original={}, corrected={}, correctorId={}",
                logId, event.getPlateNumber(), trimmed, correctorId);

        return event;
    }
}
