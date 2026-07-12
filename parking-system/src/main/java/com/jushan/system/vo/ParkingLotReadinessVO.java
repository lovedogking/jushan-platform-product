package com.jushan.system.vo;

import java.util.List;

/**
 * 停车场就绪检查结果（T22）。
 * <p>
 * 汇总停车场启用前的所有就绪检查项，按 BLOCKER/WARNING 严重级别分类。
 * 前端可将此结果用于启用按钮的状态判断和配置页面闭环展示。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingLotReadinessVO {

    private Long parkingLotId;
    private String parkingLotName;

    /** 整体是否就绪（无 BLOCKER 项即为就绪） */
    private boolean ready;

    /** 阻塞项数量 */
    private int blockerCount;

    /** 提示项数量 */
    private int warningCount;

    /** 详细检查项列表 */
    private List<ReadinessItem> items;

    // ==================== getter / setter ====================

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }

    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    public int getBlockerCount() { return blockerCount; }
    public void setBlockerCount(int blockerCount) { this.blockerCount = blockerCount; }

    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

    public List<ReadinessItem> getItems() { return items; }
    public void setItems(List<ReadinessItem> items) { this.items = items; }
}
