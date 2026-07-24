package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * P云停车记录同步日志实体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("parking_record_sync_log")
public class ParkingRecordSyncLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long parkingLotId;
    private Long parkingRecordId;

    /** 同步类型：ENTER-入场, LEAVE-离场, UPDATE-更新 */
    private String syncType;

    /** P云停车场UUID */
    private String parkUuid;

    /** 停车流水号 */
    private String parkingSerial;

    /** 车牌号 */
    private String plate;

    /** 状态：PENDING-待同步, SUCCESS-成功, FAILED-失败, RETRYING-重试中 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** P云响应码 */
    private String responseCode;

    /** P云响应消息 */
    private String responseMsg;

    /** 请求原始报文 */
    private String requestRaw;

    /** 响应原始报文 */
    private String responseRaw;

    /** 同步成功时间 */
    private LocalDateTime syncedAt;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // 常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RETRYING = "RETRYING";

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public Long getParkingRecordId() { return parkingRecordId; }
    public void setParkingRecordId(Long parkingRecordId) { this.parkingRecordId = parkingRecordId; }
    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }
    public String getParkUuid() { return parkUuid; }
    public void setParkUuid(String parkUuid) { this.parkUuid = parkUuid; }
    public String getParkingSerial() { return parkingSerial; }
    public void setParkingSerial(String parkingSerial) { this.parkingSerial = parkingSerial; }
    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getResponseMsg() { return responseMsg; }
    public void setResponseMsg(String responseMsg) { this.responseMsg = responseMsg; }
    public String getRequestRaw() { return requestRaw; }
    public void setRequestRaw(String requestRaw) { this.requestRaw = requestRaw; }
    public String getResponseRaw() { return responseRaw; }
    public void setResponseRaw(String responseRaw) { this.responseRaw = responseRaw; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
