package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 车辆续费记录（Phase 1 A1）。
 * <p>
 * 记录每次月卡续费操作的完整历史，与 {@code sys_vehicle.valid_end_date} 联动。
 * 每次 {@link MonthlyPassService#renew} 成功后写入一条记录。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("vehicle_renewal_log")
public class VehicleRenewalLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long parkingLotId;
    private Long vehicleId;
    /** 月卡ID（新体系 monthly_pass.id；旧记录为 NULL） */
    private Long monthlyPassId;
    private String plateNumber;
    private Long orderId;
    private Integer renewalMonths;
    private Integer amountCents;
    private LocalDate oldValidEnd;
    private LocalDate newValidEnd;
    private Long operatorId;
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

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public Long getMonthlyPassId() { return monthlyPassId; }
    public void setMonthlyPassId(Long monthlyPassId) { this.monthlyPassId = monthlyPassId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Integer getRenewalMonths() { return renewalMonths; }
    public void setRenewalMonths(Integer renewalMonths) { this.renewalMonths = renewalMonths; }

    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }

    public LocalDate getOldValidEnd() { return oldValidEnd; }
    public void setOldValidEnd(LocalDate oldValidEnd) { this.oldValidEnd = oldValidEnd; }

    public LocalDate getNewValidEnd() { return newValidEnd; }
    public void setNewValidEnd(LocalDate newValidEnd) { this.newValidEnd = newValidEnd; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
