package com.jushan.platform.modules.parking.task;

import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时会话自动关闭定时任务（GAP-06）。
 * <p>
 * 每日凌晨 0:05 执行，关闭超过 24 小时未出场的 IN 会话。
 * status → EXCEPTION，remark → "超时未出场自动关闭"。
 *
 * @author Jushan Platform
 * @since 1.3.0
 */
@Slf4j
@Component
public class SessionTimeoutTask {

    private static final int BATCH_SIZE = 200;

    private final ParkingSessionMapper parkingSessionMapper;

    public SessionTimeoutTask(ParkingSessionMapper parkingSessionMapper) {
        this.parkingSessionMapper = parkingSessionMapper;
    }

    @Scheduled(cron = "0 5 0 * * ?")
    public void closeTimeoutSessions() {
        log.info("开始执行超时会话自动关闭扫描");

        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int totalClosed = 0;

        // 分批查询在场超过 24 小时的会话
        List<ParkingSession> batch;
        do {
            batch = parkingSessionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingSession>()
                            .eq(ParkingSession::getStatus, ParkingSession.STATUS_IN)
                            .lt(ParkingSession::getEntryTime, cutoff)
                            .last("LIMIT " + BATCH_SIZE)
            );
            for (ParkingSession session : batch) {
                session.setStatus(ParkingSession.STATUS_EXCEPTION);
                session.setRemark("超时未出场自动关闭");
                session.setUpdatedAt(LocalDateTime.now());
                parkingSessionMapper.updateById(session);
                totalClosed++;
            }
        } while (batch.size() >= BATCH_SIZE);

        if (totalClosed > 0) {
            log.info("超时会话自动关闭完成: 关闭 {} 条", totalClosed);
        }
    }
}
