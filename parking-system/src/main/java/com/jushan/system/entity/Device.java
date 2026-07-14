package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台设备台账实体（T20）。
 * <p>
 * 本实体是平台设备业务配置的<b>唯一主数据源</b>。
 * 设备通过 parking_lot_id → tenant_id 推导租户范围。
 * device_sn 在同厂商内唯一（UNIQUE(vendor_id, device_sn)），是调用 Device Access 的可信映射。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>前端只能提交平台设备 ID，禁止直接传入 device_sn</li>
 *   <li>后端从本表可信记录读取 device_sn</li>
 *   <li>SN 不能跨租户/停车场复用</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("device")
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属停车场 ID */
    private Long parkingLotId;

    /** 绑定车道 ID（T21 负责绑定，当前可为空） */
    private Long laneId;

    /**
     * 执行设备 ID（仅 GATE 设备使用）。
     * <p>
     * 指向实际控制继电器开闸的 CAMERA 设备 ID。
     * CAMERA 设备此字段为 null（自身即为执行者）。
     * </p>
     */
    private Long executorDeviceId;

    /** 设备厂商 ID */
    private Long vendorId;

    /** 设备型号 ID */
    private Long modelId;

    /** 设备名称（运营可读） */
    private String name;

    /** 设备业务编码（停车场内唯一） */
    private String code;

    /** 厂商设备序列号（可信记录，禁止前端传入） */
    private String deviceSn;

    /** 设备类型：CAMERA-相机, GATE-道闸 */
    private String deviceType;

    /** 状态：ENABLED-启用, DISABLED-停用 */
    private String status;

    /** 设备能力（JSON 或逗号分隔，如 RECOGNIZE,CAPTURE,GATE_OPEN） */
    private String capabilities;

    /** 备注 */
    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public Long getExecutorDeviceId() { return executorDeviceId; }
    public void setExecutorDeviceId(Long executorDeviceId) { this.executorDeviceId = executorDeviceId; }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDeviceSn() { return deviceSn; }
    public void setDeviceSn(String deviceSn) { this.deviceSn = deviceSn; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
