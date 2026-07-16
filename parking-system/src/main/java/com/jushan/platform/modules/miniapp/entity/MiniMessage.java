package com.jushan.platform.modules.miniapp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 小程序消息通知实体（Phase 3 E3）。
 * <p>
 * 存储支付成功通知等消息，供小程序消息中心展示。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("mini_message")
public class MiniMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 类型常量 ====================

    /** 支付成功通知 */
    public static final String TYPE_PAY_SUCCESS = "PAY_SUCCESS";
    /** 系统通知 */
    public static final String TYPE_SYSTEM = "SYSTEM";

    // ==================== 字段 ====================

    private Long id;
    private Long tenantId;
    private Long wxUserId;
    private String type;
    private String title;
    private String content;
    private Long relatedOrderId;
    private String relatedPlate;
    private Integer relatedAmount;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getWxUserId() { return wxUserId; }
    public void setWxUserId(Long wxUserId) { this.wxUserId = wxUserId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getRelatedOrderId() { return relatedOrderId; }
    public void setRelatedOrderId(Long relatedOrderId) { this.relatedOrderId = relatedOrderId; }

    public String getRelatedPlate() { return relatedPlate; }
    public void setRelatedPlate(String relatedPlate) { this.relatedPlate = relatedPlate; }

    public Integer getRelatedAmount() { return relatedAmount; }
    public void setRelatedAmount(Integer relatedAmount) { this.relatedAmount = relatedAmount; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
