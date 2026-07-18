package com.jushan.system.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 白名单同步全量响应（任务包 7-1）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class WhitelistSyncResponse {

    /** 停车场 ID */
    private Long parkingLotId;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 总条目数 */
    private int totalCount;

    /** 白名单条目列表 */
    private List<WhitelistEntry> entries;

    // ==================== getter / setter ====================

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public List<WhitelistEntry> getEntries() { return entries; }
    public void setEntries(List<WhitelistEntry> entries) { this.entries = entries; }
}
