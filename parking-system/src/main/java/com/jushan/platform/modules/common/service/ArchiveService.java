package com.jushan.platform.modules.common.service;

import com.jushan.platform.modules.common.entity.ArchiveData;
import com.jushan.platform.modules.common.entity.ArchiveJobLog;
import com.jushan.platform.modules.common.mapper.ArchiveDataMapper;
import com.jushan.platform.modules.common.mapper.ArchiveJobLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据归档服务（Sprint 9）。
 * <p>
 * 提供超期数据扫描、归档记录管理和归档任务执行日志。
 * <p>
 * 归档策略（按 AGENTS.md 14.4 数据归档）：
 * <ul>
 *   <li>操作日志：线上保留 1 年，归档后保留 3 年</li>
 *   <li>车辆进出记录：线上保留 3 年，归档后保留 5 年以上</li>
 *   <li>订单数据：线上保留 2 年，归档后保留 5 年</li>
 *   <li>支付流水：线上保留 5 年，归档后永久保留</li>
 *   <li>抓拍图片：线上保留 30 天（正常）/90 天（异常），争议解决后保留</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

    private final ArchiveDataMapper archiveDataMapper;
    private final ArchiveJobLogMapper archiveJobLogMapper;

    public ArchiveService(ArchiveDataMapper archiveDataMapper,
                          ArchiveJobLogMapper archiveJobLogMapper) {
        this.archiveDataMapper = archiveDataMapper;
        this.archiveJobLogMapper = archiveJobLogMapper;
    }

    /**
     * 创建归档记录。
     *
     * @param dataType    数据类型
     * @param sourceTable 源表名
     * @param sourceId    源记录 ID
     * @param tenantId    租户 ID
     * @param startTime   数据起始时间
     * @param endTime     数据结束时间
     * @param archivePath 归档路径
     * @return 归档记录
     */
    public ArchiveData createArchiveRecord(String dataType, String sourceTable,
                                            Long sourceId, Long tenantId,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            String archivePath) {
        ArchiveData archive = new ArchiveData();
        archive.setTenantId(tenantId);
        archive.setDataType(dataType);
        archive.setSourceTable(sourceTable);
        archive.setSourceId(sourceId);
        archive.setArchiveBatch(generateBatchNo());
        archive.setArchivePath(archivePath);
        archive.setArchiveSize(0L);
        archive.setRecordCount(1);
        archive.setStartTime(startTime);
        archive.setEndTime(endTime);
        archive.setStatus(ArchiveData.STATUS_ARCHIVED);
        archive.setCompressed(1);
        archive.setCreatedAt(LocalDateTime.now());
        archive.setUpdatedAt(LocalDateTime.now());

        archiveDataMapper.insert(archive);
        log.info("归档记录创建成功: id={} dataType={} sourceId={}", archive.getId(), dataType, sourceId);
        return archive;
    }

    /**
     * 查询归档记录列表。
     *
     * @param tenantId 租户 ID
     * @param dataType 数据类型（可选）
     * @return 归档记录列表
     */
    public List<ArchiveData> listArchiveRecords(Long tenantId, String dataType) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ArchiveData> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ArchiveData>()
                        .eq("tenant_id", tenantId)
                        .isNull("deleted_at")
                        .orderByDesc("created_at");
        if (dataType != null && !dataType.isEmpty()) {
            wrapper.eq("data_type", dataType);
        }
        return archiveDataMapper.selectList(wrapper);
    }

    /**
     * 记录归档任务执行日志。
     *
     * @param jobName     任务名称
     * @param jobType     任务类型
     * @param dataType    数据类型
     * @param recordCount 处理记录数
     * @param successCount 成功记录数
     * @param failCount   失败记录数
     * @param status      状态
     * @param errorMsg    错误信息
     * @return 任务日志
     */
    public ArchiveJobLog recordJobLog(String jobName, String jobType, String dataType,
                                       int recordCount, int successCount, int failCount,
                                       String status, String errorMsg) {
        ArchiveJobLog jobLog = new ArchiveJobLog();
        jobLog.setJobName(jobName);
        jobLog.setJobType(jobType);
        jobLog.setDataType(dataType);
        jobLog.setStartTime(LocalDateTime.now());
        jobLog.setRecordCount(recordCount);
        jobLog.setSuccessCount(successCount);
        jobLog.setFailCount(failCount);
        jobLog.setStatus(status);
        jobLog.setErrorMsg(errorMsg);
        if (!ArchiveJobLog.STATUS_RUNNING.equals(status)) {
            jobLog.setEndTime(LocalDateTime.now());
        }
        jobLog.setCreatedAt(LocalDateTime.now());
        jobLog.setUpdatedAt(LocalDateTime.now());

        archiveJobLogMapper.insert(jobLog);
        return jobLog;
    }

    /**
     * 更新任务日志状态。
     *
     * @param jobLogId   任务日志 ID
     * @param status     新状态
     * @param successCount 成功数
     * @param failCount  失败数
     * @param errorMsg   错误信息
     */
    public void updateJobLogStatus(Long jobLogId, String status, int successCount, int failCount, String errorMsg) {
        ArchiveJobLog jobLog = archiveJobLogMapper.selectById(jobLogId);
        if (jobLog != null) {
            jobLog.setStatus(status);
            jobLog.setSuccessCount(successCount);
            jobLog.setFailCount(failCount);
            jobLog.setErrorMsg(errorMsg);
            jobLog.setEndTime(LocalDateTime.now());
            jobLog.setUpdatedAt(LocalDateTime.now());
            archiveJobLogMapper.updateById(jobLog);
        }
    }

    /**
     * 查询归档任务日志列表。
     *
     * @param jobType  任务类型（可选）
     * @param dataType 数据类型（可选）
     * @param status   状态（可选）
     * @return 任务日志列表
     */
    public List<ArchiveJobLog> listJobLogs(String jobType, String dataType, String status) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ArchiveJobLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ArchiveJobLog>()
                        .orderByDesc("created_at");
        if (jobType != null && !jobType.isEmpty()) {
            wrapper.eq("job_type", jobType);
        }
        if (dataType != null && !dataType.isEmpty()) {
            wrapper.eq("data_type", dataType);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        return archiveJobLogMapper.selectList(wrapper);
    }

    private String generateBatchNo() {
        return "ARC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", (int) (Math.random() * 10000));
    }
}
