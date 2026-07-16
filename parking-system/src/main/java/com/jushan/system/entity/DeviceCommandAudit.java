package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备命令调用审计实体（P001）。
 * <p>
 * 记录对 Device Access 发出的控制类命令（如开闸、关闸、抓拍、校时等）的
 * 完整调用上下文、执行结果和 UNCERTAIN 处置状态。
 * <p>
 * <strong>设计目标</strong>：
 * <ul>
 *   <li>支持 v1.0 统一命令模型（commandId、commandType、targetDeviceId、executorDeviceId）</li>
 *   <li>记录操作来源、操作人、原因，满足人工兜底和审计追溯</li>
 *   <li>记录前次调用 ID，支持人工再次开闸时关联历史命令</li>
 *   <li>显式标记 UNCERTAIN 状态，网络超时等不确定场景不得自动重试</li>
 * </ul>
 * <p>
 * <strong>当前状态</strong>：Device Access v0.4 已实现开闸能力，
 * 本表记录完整的调用上下文、执行结果和 UNCERTAIN 处置状态。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("device_command_audit")
public class DeviceCommandAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** v1.0 命令 ID（全局唯一，如 cmd_01JZ...）；占位阶段可为空 */
    private String commandId;

    /** 目标租户 ID */
    private Long tenantId;

    /** 目标停车场 ID */
    private Long parkingLotId;

    /** 目标车道 ID */
    private Long laneId;

    /** 平台设备 ID（关联 device.id） */
    private Long deviceId;

    /** 调用时使用的厂商序列号（可信记录冗余，便于排障） */
    private String deviceSn;

    /** 命令类型：OPEN_GATE, CLOSE_GATE, SNAPSHOT, REBOOT, SYNC_TIME 等 */
    private String commandType;

    /** 关联车牌号（手动开闸时记录，Phase 2 D3） */
    private String plateNumber;

    /** 费用（分，手工计费时记录，Phase 2 D3） */
    private Integer feeCents;

    /** 操作来源：SYSTEM-系统自动, MANUAL-人工操作, AUTO_EXIT-自动出场, COMPENSATION-补偿 */
    private String source;

    /** 操作人 ID（sys_user.id；SYSTEM 来源时为 0） */
    private Long operatorId;

    /** 操作人名称/账号 */
    private String operatorName;

    /** 操作原因/备注（由操作人或业务填写） */
    private String reason;

    /** 前次命令 ID（人工再次开闸、重试或关联补偿时填写） */
    private String previousCommandId;

    /**
     * 命令状态。
     * <ul>
     *   <li>PENDING — 已创建审计，尚未收到最终回执</li>
     *   <li>SUCCESS — 有明确成功证据（不等于闸杆实际打开）</li>
     *   <li>FAILED — 有明确失败结果</li>
     *   <li>UNCERTAIN — 网络超时等不确定，禁止自动重试</li>
     *   <li>REJECTED — 参数/权限/能力/状态不允许</li>
     *   <li>NOT_IMPLEMENTED — 历史占位状态，v0.4 起不再使用</li>
     * </ul>
     */
    private String status;

    /** 是否处于 UNCERTAIN 状态（与 status=UNCERTAIN 一致，便于索引和查询） */
    private Boolean uncertain;

    /** 错误码（DA 返回或平台错误码） */
    private String errorCode;

    /** 错误消息 */
    private String errorMessage;

    /** 请求报文/上下文（JSON，用于排障和复核） */
    private String requestPayload;

    /** 响应报文/结果（JSON） */
    private String responsePayload;

    /** 命令发出时间 */
    private LocalDateTime issuedAt;

    /** 命令完成/最终状态确认时间 */
    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceSn() { return deviceSn; }
    public void setDeviceSn(String deviceSn) { this.deviceSn = deviceSn; }

    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Integer getFeeCents() { return feeCents; }
    public void setFeeCents(Integer feeCents) { this.feeCents = feeCents; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPreviousCommandId() { return previousCommandId; }
    public void setPreviousCommandId(String previousCommandId) { this.previousCommandId = previousCommandId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getUncertain() { return uncertain; }
    public void setUncertain(Boolean uncertain) { this.uncertain = uncertain; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }

    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
