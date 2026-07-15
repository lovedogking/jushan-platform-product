package com.jushan.platform.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 超时停放自动拉黑定时任务（PRD §5.1）。
 * <p>
 * 每 5 分钟执行一次，委托 {@link OverstayBlacklistService} 完成扫描与拉黑。
 * 任务总开关由配置 {@code jushan.overstay-blacklist.enabled} 控制（默认开启），
 * 便于在测试或维护期临时关闭。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Component
public class OverstayBlacklistScheduler {

    private final OverstayBlacklistService overstayBlacklistService;

    @Value("${jushan.overstay-blacklist.enabled:true}")
    private boolean enabled;

    public OverstayBlacklistScheduler(OverstayBlacklistService overstayBlacklistService) {
        this.overstayBlacklistService = overstayBlacklistService;
    }

    /**
     * 每 5 分钟执行一次超时停放自动拉黑检测。
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void scheduledDetect() {
        if (!enabled) {
            log.debug("超时停放自动拉黑任务已关闭（jushan.overstay-blacklist.enabled=false），跳过本次执行");
            return;
        }
        try {
            int count = overstayBlacklistService.detectAndBlacklist(LocalDateTime.now());
            if (count > 0) {
                log.info("超时停放自动拉黑：本次新增拉黑 {} 条", count);
            }
        } catch (Exception e) {
            // 定时任务异常不应中断后续调度，仅记录日志
            log.error("超时停放自动拉黑任务执行失败", e);
        }
    }
}
