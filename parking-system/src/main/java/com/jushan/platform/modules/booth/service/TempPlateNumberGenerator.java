package com.jushan.platform.modules.booth.service;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 临时车牌号生成器（任务包 3-4）。
 * <p>
 * 通过 Redis INCR 按车场+日期维度生成唯一临时车牌号，
 * 格式为 "临" + yyMMdd + 两位序号（如 "临26071701"）。
 * <p>
 * 单日单车场上限 99。Redis 不可用时降级为时间戳方案。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class TempPlateNumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(TempPlateNumberGenerator.class);

    private static final int MAX_SEQ_PER_DAY = 99;
    private static final String KEY_PREFIX = "temp_plate:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private final StringRedisTemplate redisTemplate;

    public TempPlateNumberGenerator(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * 生成临时车牌号（消耗序号）。
     *
     * @param parkingLotId 车场 ID
     * @return 临时车牌号（如 "临26071701"）
     * @throws BusinessException 当日序号超过 99 时抛出
     */
    public String generate(Long parkingLotId) {
        if (redisTemplate == null) {
            return fallbackGenerate(parkingLotId);
        }

        try {
            String today = LocalDate.now().format(DATE_FORMAT);
            String key = KEY_PREFIX + parkingLotId + ":" + today;

            Long seq = redisTemplate.opsForValue().increment(key);
            // 首次使用时设置过期：至当日 23:59:59 后自动清除
            if (seq != null && seq == 1) {
                long secondsUntilMidnight = ChronoUnit.SECONDS.between(
                        LocalDateTime.now(),
                        LocalDate.now().plusDays(1).atStartOfDay());
                redisTemplate.expire(key, Duration.ofSeconds(Math.max(secondsUntilMidnight, 60)));
            }

            if (seq == null || seq > MAX_SEQ_PER_DAY) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "当日临时车牌号已用完（已达" + MAX_SEQ_PER_DAY + "个）");
            }

            return "临" + today + String.format("%02d", seq);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis 临时车牌号生成失败，降级使用时间戳: lotId={} error={}", parkingLotId, e.getMessage());
            return fallbackGenerate(parkingLotId);
        }
    }

    /**
     * 获取建议临时车牌号（预览，不消耗序号）。
     *
     * @param parkingLotId 车场 ID
     * @return 建议临时车牌号
     */
    public String suggest(Long parkingLotId) {
        if (redisTemplate == null) {
            return fallbackGenerate(parkingLotId);
        }

        try {
            String today = LocalDate.now().format(DATE_FORMAT);
            String key = KEY_PREFIX + parkingLotId + ":" + today;
            String currentSeq = redisTemplate.opsForValue().get(key);
            long seq = (currentSeq != null ? Long.parseLong(currentSeq) : 0) + 1;
            if (seq > MAX_SEQ_PER_DAY) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "当日临时车牌号已用完");
            }
            return "临" + today + String.format("%02d", seq);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis 建议车牌号生成失败: lotId={} error={}", parkingLotId, e.getMessage());
            return fallbackGenerate(parkingLotId);
        }
    }

    /**
     * 降级方案：使用时间戳后缀生成临时车牌。
     */
    private String fallbackGenerate(Long parkingLotId) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String suffix = String.format("%04d", System.currentTimeMillis() % 10000);
        String plate = "临" + today + suffix;
        log.warn("临时车牌号降级生成（非 Redis 方案）: lotId={} plate={}", parkingLotId, plate);
        return plate;
    }
}
