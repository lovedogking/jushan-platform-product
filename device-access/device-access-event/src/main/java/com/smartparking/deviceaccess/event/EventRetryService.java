package com.smartparking.deviceaccess.event;

import com.smartparking.deviceaccess.common.entity.EventOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 事件重试服务 —— 定时扫描发件箱中的 PENDING 记录并重新推送。
 *
 * @since v0.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRetryService {

    private final EventOutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final WebhookProperties properties;

    /**
     * 每 30 秒扫描一次 PENDING 记录。
     */
    @Scheduled(fixedRate = 30_000)
    public void retryPendingEvents() {
        if (!properties.isEnabled()) {
            return;
        }

        List<EventOutbox> pending = outboxRepository.findPending();
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Retrying {} pending outbox events", pending.size());
        for (EventOutbox outbox : pending) {
            boolean success = eventPublisher.doPush(outbox.getEventId(), outbox.getEventType(), outbox.getPayload());
            if (success) {
                outboxRepository.markSent(outbox.getId());
                log.info("Outbox event retry succeeded: eventId={}", outbox.getEventId());
            } else {
                int retried = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
                retried++;
                if (retried >= properties.getRetryCount()) {
                    outboxRepository.markFailed(outbox.getId());
                    log.error("Outbox event exhausted retries and marked FAILED: eventId={}", outbox.getEventId());
                } else {
                    outboxRepository.updateRetry(outbox.getId(), retried);
                    log.warn("Outbox event retry failed: eventId={}, retryCount={}", outbox.getEventId(), retried);
                }
            }
        }
    }
}
