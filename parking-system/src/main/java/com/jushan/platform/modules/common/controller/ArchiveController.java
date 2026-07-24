// @Deprecated — 迁移至 com.jushan.platform.modules.common.controller.ArchiveController
package com.jushan.platform.modules.common.controller;

import com.jushan.common.R;
import com.jushan.platform.modules.common.entity.ArchiveData;
import com.jushan.platform.modules.common.entity.ArchiveJobLog;
import com.jushan.platform.modules.common.service.ArchiveService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据归档 Controller（Sprint 9）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/archive")
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    /**
     * 查询归档记录列表。
     */
    @GetMapping("/records")
    public R<List<ArchiveData>> listRecords(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String dataType) {
        return R.ok(archiveService.listArchiveRecords(tenantId, dataType));
    }

    /**
     * 查询归档任务日志列表。
     */
    @GetMapping("/job-logs")
    public R<List<ArchiveJobLog>> listJobLogs(
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) String status) {
        return R.ok(archiveService.listJobLogs(jobType, dataType, status));
    }
}
