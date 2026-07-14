package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据归档记录实体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("archive_data")
public class ArchiveData implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 数据类型：OPERATION_LOG, ACCESS_LOG, PARKING_RECORD, ORDER, PAY_ORDER */
    private String dataType;

    /** 源表名 */
    private String sourceTable;

    /** 源记录 ID */
    private Long sourceId;

    /** 归档批次号 */
    private String archiveBatch;

    /** 归档存储路径 */
    private String archivePath;

    /** 归档数据大小（字节） */
    private Long archiveSize;

    /** 归档记录数 */
    private Integer recordCount;

    /** 归档数据起始时间 */
    private LocalDateTime startTime;

    /** 归档数据结束时间 */
    private LocalDateTime endTime;

    /** 状态：ARCHIVED-已归档, RESTORED-已恢复, DELETED-已删除 */
    private String status;

    /** 数据校验和（SHA-256） */
    private String checksum;

    /** 是否压缩：1-是, 0-否 */
    private Integer compressed;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // 常量
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_RESTORED = "RESTORED";
    public static final String STATUS_DELETED = "DELETED";

    public static final String DATA_TYPE_OPERATION_LOG = "OPERATION_LOG";
    public static final String DATA_TYPE_ACCESS_LOG = "ACCESS_LOG";
    public static final String DATA_TYPE_PARKING_RECORD = "PARKING_RECORD";
    public static final String DATA_TYPE_ORDER = "ORDER";
    public static final String DATA_TYPE_PAY_ORDER = "PAY_ORDER";

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getArchiveBatch() { return archiveBatch; }
    public void setArchiveBatch(String archiveBatch) { this.archiveBatch = archiveBatch; }
    public String getArchivePath() { return archivePath; }
    public void setArchivePath(String archivePath) { this.archivePath = archivePath; }
    public Long getArchiveSize() { return archiveSize; }
    public void setArchiveSize(Long archiveSize) { this.archiveSize = archiveSize; }
    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public Integer getCompressed() { return compressed; }
    public void setCompressed(Integer compressed) { this.compressed = compressed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
