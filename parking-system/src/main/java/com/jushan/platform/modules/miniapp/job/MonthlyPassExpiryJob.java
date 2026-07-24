package com.jushan.platform.modules.miniapp.job;

import com.jushan.platform.modules.miniapp.mapper.MonthlyPassMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 月卡到期定时任务（任务包 3-1）。
 * <p>
 * 每日凌晨 2:00 扫描到期月卡，置为 EXPIRED。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Component
public class MonthlyPassExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassExpiryJob.class);

    private final MonthlyPassMapper monthlyPassMapper;

    public MonthlyPassExpiryJob(MonthlyPassMapper monthlyPassMapper) {
        this.monthlyPassMapper = monthlyPassMapper;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void expireMonthlyPasses() {
        int updated = monthlyPassMapper.expireActivePasses(LocalDate.now());
        if (updated > 0) {
            log.info("月卡到期处理完成: 更新 {} 条记录为 EXPIRED", updated);
        }
    }
}
