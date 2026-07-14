package com.jushan.platform.modules.authcode.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 授权码视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class SysAuthCodeVO {

    /** 授权码 ID */
    private Long id;

    /** 授权码 */
    private String code;

    /** 激活后绑定的租户 ID */
    private Long tenantId;

    /** 可开通车场数量 */
    private Integer maxParkingCount;

    /** 有效期开始 */
    private LocalDate validStart;

    /** 有效期结束 */
    private LocalDate validEnd;

    /** 已使用次数 */
    private Integer usedCount;

    /** 最大使用次数 */
    private Integer maxUseCount;

    /** 功能版本类型 */
    private String versionType;

    /** 状态：0未使用, 1已激活, 2已过期, 3已禁用 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 版本类型描述 */
    private String versionTypeDesc;

    /** 激活人 ID */
    private Long activatedBy;

    /** 激活时间 */
    private LocalDateTime activatedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Integer getMaxParkingCount() { return maxParkingCount; }
    public void setMaxParkingCount(Integer maxParkingCount) { this.maxParkingCount = maxParkingCount; }

    public LocalDate getValidStart() { return validStart; }
    public void setValidStart(LocalDate validStart) { this.validStart = validStart; }

    public LocalDate getValidEnd() { return validEnd; }
    public void setValidEnd(LocalDate validEnd) { this.validEnd = validEnd; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public Integer getMaxUseCount() { return maxUseCount; }
    public void setMaxUseCount(Integer maxUseCount) { this.maxUseCount = maxUseCount; }

    public String getVersionType() { return versionType; }
    public void setVersionType(String versionType) { this.versionType = versionType; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public String getVersionTypeDesc() { return versionTypeDesc; }
    public void setVersionTypeDesc(String versionTypeDesc) { this.versionTypeDesc = versionTypeDesc; }

    public Long getActivatedBy() { return activatedBy; }
    public void setActivatedBy(Long activatedBy) { this.activatedBy = activatedBy; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
