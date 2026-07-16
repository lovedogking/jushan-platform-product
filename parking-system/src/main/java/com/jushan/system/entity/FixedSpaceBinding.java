package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 固定车位绑定关系（Phase 1 A2）。
 * <p>
 * 记录「车位号 ↔ 车辆」的专属绑定关系。
 * 非绑定车辆占用该车位时按临停计费。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("fixed_space_binding")
public class FixedSpaceBinding implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：生效中 */
    public static final int STATUS_ACTIVE = 1;
    /** 状态：已过期 */
    public static final int STATUS_EXPIRED = 2;
    /** 状态：已注销 */
    public static final int STATUS_DISABLED = 3;

    private Long id;
    private Long tenantId;
    private Long parkingLotId;
    private Long zoneId;
    private String spaceNo;
    private Long vehicleId;
    private LocalDate validStart;
    private LocalDate validEnd;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getSpaceNo() { return spaceNo; }
    public void setSpaceNo(String spaceNo) { this.spaceNo = spaceNo; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public LocalDate getValidStart() { return validStart; }
    public void setValidStart(LocalDate validStart) { this.validStart = validStart; }
    public LocalDate getValidEnd() { return validEnd; }
    public void setValidEnd(LocalDate validEnd) { this.validEnd = validEnd; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
