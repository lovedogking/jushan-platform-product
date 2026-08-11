package com.jushan.platform.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.entity.FeeRule;
import com.jushan.platform.modules.parking.entity.FeeRuleHistory;
import com.jushan.platform.modules.parking.entity.FeeRuleSegment;
import com.jushan.platform.modules.parking.mapper.FeeRuleHistoryMapper;
import com.jushan.platform.modules.parking.mapper.FeeRuleMapper;
import com.jushan.platform.modules.parking.mapper.FeeRuleSegmentMapper;
import com.jushan.platform.modules.parking.service.FeeRuleHistoryService;
import com.jushan.platform.modules.parking.vo.FeeRuleHistoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收费规则版本历史 Service 实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeRuleHistoryServiceImpl implements FeeRuleHistoryService {

    private final FeeRuleHistoryMapper historyMapper;
    private final FeeRuleMapper feeRuleMapper;
    private final FeeRuleSegmentMapper segmentMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSnapshot(FeeRule feeRule) {
        if (feeRule == null || feeRule.getId() == null) {
            return;
        }

        // 查询当前最新历史版本，将其 effective_to 设为当前时间
        LambdaQueryWrapper<FeeRuleHistory> wrapper = new LambdaQueryWrapper<FeeRuleHistory>()
                .eq(FeeRuleHistory::getFeeRuleId, feeRule.getId())
                .isNull(FeeRuleHistory::getEffectiveTo)
                .isNull(FeeRuleHistory::getDeletedAt)
                .orderByDesc(FeeRuleHistory::getVersionNo)
                .last("LIMIT 1");
        FeeRuleHistory latest = historyMapper.selectOne(wrapper);
        if (latest != null) {
            latest.setEffectiveTo(LocalDateTime.now());
            historyMapper.updateById(latest);
        }

        // 构建快照：规则 + 时段列表
        List<FeeRuleSegment> segments = segmentMapper.selectListByFeeRuleId(feeRule.getId());
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("rule", feeRule);
        snapshot.put("segments", segments);

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("FeeRule 快照序列化失败: ruleId={}", feeRule.getId(), e);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "规则快照失败");
        }

        Integer maxVersionNo = historyMapper.selectMaxVersionNo(feeRule.getId());
        int nextVersionNo = (maxVersionNo == null ? 0 : maxVersionNo) + 1;

        FeeRuleHistory history = new FeeRuleHistory();
        history.setTenantId(feeRule.getTenantId());
        history.setFeeRuleId(feeRule.getId());
        history.setVersionNo(nextVersionNo);
        history.setSnapshotJson(snapshotJson);
        history.setEffectiveFrom(LocalDateTime.now());
        // 修改人 ID：优先取当前登录用户；无人时为空
        history.setCreatedBy(TenantContext.getUserId());

        historyMapper.insert(history);
        log.info("FeeRule 快照已保存: ruleId={} versionNo={}", feeRule.getId(), nextVersionNo);
    }

    @Override
    public List<FeeRuleHistoryVO> listByFeeRuleId(Long feeRuleId) {
        if (feeRuleId == null) {
            return List.of();
        }
        List<FeeRuleHistory> list = historyMapper.selectListByFeeRuleId(feeRuleId);
        return list.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeRule rollback(Long historyId) {
        if (historyId == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "历史版本 ID 不能为空");
        }

        FeeRuleHistory history = historyMapper.selectById(historyId);
        if (history == null || history.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "历史版本不存在");
        }

        // 先把当前规则再快照一次（保留当前状态）
        FeeRule current = feeRuleMapper.selectById(history.getFeeRuleId());
        if (current == null || current.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "当前规则不存在");
        }
        saveSnapshot(current);

        // 反序列化历史快照
        Map<String, Object> snapshot;
        try {
            snapshot = objectMapper.readValue(history.getSnapshotJson(), Map.class);
        } catch (JsonProcessingException e) {
            log.error("FeeRule 快照反序列化失败: historyId={}", historyId, e);
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "历史快照解析失败");
        }

        FeeRule snapshotRule = objectMapper.convertValue(snapshot.get("rule"), FeeRule.class);
        List<FeeRuleSegment> snapshotSegments = objectMapper.convertValue(
                snapshot.get("segments"), objectMapper.getTypeFactory().constructCollectionType(List.class, FeeRuleSegment.class));

        // 回写当前规则（保留 id/tenant_id/created_at 等基础字段）
        current.setName(snapshotRule.getName());
        current.setDescription(snapshotRule.getDescription());
        current.setLotId(snapshotRule.getLotId());
        current.setZoneId(snapshotRule.getZoneId());
        current.setBillingMode(snapshotRule.getBillingMode());
        current.setVehicleType(snapshotRule.getVehicleType());
        current.setPlateColor(snapshotRule.getPlateColor());
        current.setFreeMinutes(snapshotRule.getFreeMinutes());
        current.setUnitMinutes(snapshotRule.getUnitMinutes());
        current.setFirstPeriodMinutes(snapshotRule.getFirstPeriodMinutes());
        current.setFirstPeriodPrice(snapshotRule.getFirstPeriodPrice());
        current.setSubsequentPrice(snapshotRule.getSubsequentPrice());
        current.setDailyCap(snapshotRule.getDailyCap());
        current.setMaxAmount(snapshotRule.getMaxAmount());
        current.setNightCap(snapshotRule.getNightCap());
        current.setCrossDayMode(snapshotRule.getCrossDayMode());
        current.setEffectMode(snapshotRule.getEffectMode());
        current.setPriority(snapshotRule.getPriority());
        current.setStatus(snapshotRule.getStatus());
        current.setEffectiveStart(snapshotRule.getEffectiveStart());
        current.setEffectiveEnd(snapshotRule.getEffectiveEnd());
        current.setHolidayRules(snapshotRule.getHolidayRules());
        current.setVersion(current.getVersion() + 1);
        current.setUpdatedAt(LocalDateTime.now());
        feeRuleMapper.updateById(current);

        // 重建时段：删除当前时段，插入历史时段（ID 重新生成）
        segmentMapper.delete(new LambdaQueryWrapper<FeeRuleSegment>()
                .eq(FeeRuleSegment::getFeeRuleId, current.getId()));
        if (snapshotSegments != null && !snapshotSegments.isEmpty()) {
            for (FeeRuleSegment segment : snapshotSegments) {
                segment.setId(null);
                segment.setFeeRuleId(current.getId());
                segment.setTenantId(current.getTenantId());
                segment.setDeletedAt(null);
                segment.setCreatedAt(LocalDateTime.now());
                segment.setUpdatedAt(LocalDateTime.now());
                segmentMapper.insert(segment);
            }
        }

        log.info("FeeRule 已回退: ruleId={} to historyId={} versionNo={}",
                current.getId(), historyId, history.getVersionNo());
        return current;
    }

    private FeeRuleHistoryVO toVO(FeeRuleHistory history) {
        FeeRuleHistoryVO vo = new FeeRuleHistoryVO();
        vo.setId(history.getId());
        vo.setFeeRuleId(history.getFeeRuleId());
        vo.setVersionNo(history.getVersionNo());
        vo.setEffectiveFrom(history.getEffectiveFrom());
        vo.setEffectiveTo(history.getEffectiveTo());
        vo.setCreatedBy(history.getCreatedBy());
        vo.setCreatedAt(history.getCreatedAt());
        return vo;
    }
}
