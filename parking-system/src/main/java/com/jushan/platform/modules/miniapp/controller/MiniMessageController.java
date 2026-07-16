package com.jushan.platform.modules.miniapp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.miniapp.entity.MiniMessage;
import com.jushan.platform.modules.miniapp.mapper.MiniMessageMapper;
import com.jushan.system.service.WxUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 小程序消息通知控制器（Phase 3 E3）。
 * <p>
 * 提供消息列表查询、已读标记等能力。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/mini/messages")
public class MiniMessageController {

    private static final Logger log = LoggerFactory.getLogger(MiniMessageController.class);

    private final MiniMessageMapper miniMessageMapper;
    private final WxUserService wxUserService;

    public MiniMessageController(MiniMessageMapper miniMessageMapper,
                                  WxUserService wxUserService) {
        this.miniMessageMapper = miniMessageMapper;
        this.wxUserService = wxUserService;
    }

    /**
     * 查询消息列表（分页）。
     * <p>
     * 返回当前微信用户的消息，按创建时间降序排列。
     *
     * @param current 页码
     * @param size    每页条数
     * @return 消息分页列表
     */
    @GetMapping
    @RequirePermission("miniapp:view")
    public R<IPage<Map<String, Object>>> listMessages(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        Long wxUserId = wxUserService.getCurrentWxUserId();

        Page<MiniMessage> page = new Page<>(current, size);
        LambdaQueryWrapper<MiniMessage> wrapper = new LambdaQueryWrapper<MiniMessage>()
                .eq(MiniMessage::getWxUserId, wxUserId)
                .isNull(MiniMessage::getDeletedAt)
                .orderByDesc(MiniMessage::getCreatedAt);

        IPage<MiniMessage> entityPage = miniMessageMapper.selectPage(page, wrapper);
        IPage<Map<String, Object>> resultPage = entityPage.convert(this::toMessageMap);

        return R.ok(resultPage);
    }

    /**
     * 标记消息为已读。
     *
     * @param id 消息ID
     * @return 处理结果
     */
    @PutMapping("/{id}/read")
    @RequirePermission("miniapp:view")
    public R<?> markAsRead(@PathVariable Long id) {
        Long wxUserId = wxUserService.getCurrentWxUserId();

        MiniMessage message = miniMessageMapper.selectById(id);
        if (message == null) {
            return R.fail(1003, "消息不存在");
        }
        if (!message.getWxUserId().equals(wxUserId)) {
            return R.fail(1003, "无权操作此消息");
        }
        if (Boolean.TRUE.equals(message.getIsRead())) {
            return R.ok(Map.of("id", id, "isRead", true));
        }

        message.setIsRead(true);
        message.setReadAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        miniMessageMapper.updateById(message);

        log.debug("消息已标记为已读: id={} wxUserId={}", id, wxUserId);
        return R.ok(Map.of("id", id, "isRead", true));
    }

    /**
     * 查询未读消息数量。
     *
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    @RequirePermission("miniapp:view")
    public R<Map<String, Object>> getUnreadCount() {
        Long wxUserId = wxUserService.getCurrentWxUserId();

        long count = miniMessageMapper.selectCount(
                new LambdaQueryWrapper<MiniMessage>()
                        .eq(MiniMessage::getWxUserId, wxUserId)
                        .eq(MiniMessage::getIsRead, false)
                        .isNull(MiniMessage::getDeletedAt));

        return R.ok(Map.of("unreadCount", count));
    }

    /**
     * 将 MiniMessage 实体转换为前端展示 Map。
     */
    private Map<String, Object> toMessageMap(MiniMessage msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", msg.getId());
        map.put("type", msg.getType());
        map.put("title", msg.getTitle());
        map.put("content", msg.getContent());
        map.put("isRead", msg.getIsRead());
        map.put("relatedOrderId", msg.getRelatedOrderId());
        map.put("relatedPlate", msg.getRelatedPlate());
        map.put("relatedAmount", msg.getRelatedAmount());
        map.put("relatedAmountYuan", msg.getRelatedAmount() != null
                ? String.format("%.2f", msg.getRelatedAmount() / 100.0) : null);
        map.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
        map.put("readAt", msg.getReadAt() != null ? msg.getReadAt().toString() : null);
        return map;
    }
}
