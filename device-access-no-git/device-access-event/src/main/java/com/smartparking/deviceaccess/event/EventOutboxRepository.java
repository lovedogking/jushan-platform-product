package com.smartparking.deviceaccess.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.deviceaccess.common.entity.EventOutbox;
import com.smartparking.deviceaccess.common.mapper.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件发件箱持久化服务。
 *
 * @since v0.4
 */
@Component
@RequiredArgsConstructor
public class EventOutboxRepository {

    private final EventOutboxMapper mapper;

    /**
     * 保存待重试事件。
     */
    public void savePending(DeviceEvent event, String payload) {
        EventOutbox outbox = new EventOutbox();
        outbox.setEventId(event.getEventId());
        outbox.setEventType(event.getEventType());
        outbox.setPayload(payload);
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setCreatedAt(LocalDateTime.now());
        mapper.insert(outbox);
    }

    /**
     * 查询 PENDING 记录，按创建时间升序（先产生的事件先重试）。
     */
    public List<EventOutbox> findPending() {
        LambdaQueryWrapper<EventOutbox> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventOutbox::getStatus, "PENDING")
                .orderByAsc(EventOutbox::getCreatedAt);
        return mapper.selectList(wrapper);
    }

    /**
     * 标记为已发送。
     */
    public void markSent(Long id) {
        EventOutbox update = new EventOutbox();
        update.setId(id);
        update.setStatus("SENT");
        update.setLastRetryAt(LocalDateTime.now());
        mapper.updateById(update);
    }

    /**
     * 更新重试状态（仍 PENDING）。
     */
    public void updateRetry(Long id, int retryCount) {
        EventOutbox update = new EventOutbox();
        update.setId(id);
        update.setRetryCount(retryCount);
        update.setLastRetryAt(LocalDateTime.now());
        mapper.updateById(update);
    }

    /**
     * 标记为最终失败。
     */
    public void markFailed(Long id) {
        EventOutbox update = new EventOutbox();
        update.setId(id);
        update.setStatus("FAILED");
        update.setLastRetryAt(LocalDateTime.now());
        mapper.updateById(update);
    }
}
