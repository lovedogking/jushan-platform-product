package com.jushan.system.task;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jushan.system.entity.FixedSpaceBinding;
import com.jushan.system.mapper.FixedSpaceBindingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 固定车位到期自动标记定时任务（任务包 3-2）。
 * <p>
 * 每日 00:05 执行，扫描所有 status=ACTIVE 且 valid_end < 当日的固定车位绑定记录，
 * 将状态批量更新为 EXPIRED（status=2）。
 * <p>
 * 任务总开关由配置 {@code jushan.fixed-space-expiry.enabled} 控制（默认开启）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Component
public class FixedSpaceExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FixedSpaceExpiryScheduler.class);

    private final FixedSpaceBindingMapper bindingMapper;

    @Value("${jushan.fixed-space-expiry.enabled:true}")
    private boolean enabled;

    public FixedSpaceExpiryScheduler(FixedSpaceBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

    /**
     * 每日 00:05 执行固定车位到期标记。
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void scheduledExpire() {
        if (!enabled) {
            log.debug("固定车位到期标记任务已关闭（jushan.fixed-space-expiry.enabled=false），跳过本次执行");
            return;
        }
        try {
            LocalDate today = LocalDate.now();
            UpdateWrapper<FixedSpaceBinding> updateWrapper = new UpdateWrapper<FixedSpaceBinding>()
                    .set("status", FixedSpaceBinding.STATUS_EXPIRED)
                    .eq("status", FixedSpaceBinding.STATUS_ACTIVE)
                    .lt("valid_end", today);

            int count = bindingMapper.update(null, updateWrapper);
            if (count > 0) {
                log.info("固定车位到期标记：本次标记 {} 条过期记录", count);
            }
        } catch (Exception e) {
            log.error("固定车位到期标记任务执行失败", e);
        }
    }
}
