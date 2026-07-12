package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.DeviceStatusSnapshot;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DeviceStatusSnapshotMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.vo.ParkingLotReadinessVO;
import com.jushan.system.vo.ReadinessItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 停车场就绪检查服务（T22）。
 * <p>
 * 在停车场<b>启用前</b>检查车道配置、设备绑定、执行相机、容量等关键项。
 * 检查结果分 BLOCKER（阻塞启用）和 WARNING（提示但不阻塞）两级。
 * 未实现模块（收费规则、支付配置、设备在线）以占位项明确标记 {@code implemented=false}。
 * <p>
 * <strong>使用方式</strong>：
 * <ul>
 *   <li>运营端/总后台在启用停车场前调用本接口</li>
 *   <li>前端根据返回的 items 展示配置页面闭环</li>
 *   <li>本服务不拦截启用操作本身，只是提供检查结果供调用方决策</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class ReadinessCheckService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessCheckService.class);

    private final ParkingLotScopeResolver scopeResolver;

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String DEVICE_TYPE_CAMERA = "CAMERA";
    private static final String DEVICE_TYPE_GATE = "GATE";

    // 检查分类
    private static final String CAT_LANE = "LANE";
    private static final String CAT_DEVICE = "DEVICE";
    private static final String CAT_CAPACITY = "CAPACITY";
    private static final String CAT_CHARGE_RULE = "CHARGE_RULE";
    private static final String CAT_PAYMENT = "PAYMENT";
    private static final String CAT_DEVICE_STATUS = "DEVICE_STATUS";

    // 入口/出口方向集合（MIXED 同时属于入口和出口）
    private static final List<String> ENTRY_DIRECTIONS = List.of("ENTRY", "MIXED");
    private static final List<String> EXIT_DIRECTIONS = List.of("EXIT", "MIXED");

    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceStatusSnapshotMapper snapshotMapper;

    /** 快照过期阈值（秒），超过则视为设备在线状态未知 */
    private static final long SNAPSHOT_STALE_SECONDS = 120;

    public ReadinessCheckService(ParkingLotMapper parkingLotMapper,
                                  ParkingLaneMapper laneMapper,
                                  DeviceMapper deviceMapper,
                                  DeviceStatusSnapshotMapper snapshotMapper,
                                  ParkingLotScopeResolver scopeResolver) {
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.deviceMapper = deviceMapper;
        this.snapshotMapper = snapshotMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 对指定停车场执行就绪检查。
     *
     * @param parkingLotId 停车场 ID
     * @return 就绪检查结果
     */
    public ParkingLotReadinessVO check(Long parkingLotId) {
        // 1. 校验停车场归属（租户隔离）
        ParkingLot lot = getParkingLotWithAuth(parkingLotId);

        // 2. 查询所有车道
        List<ParkingLane> allLanes = laneMapper.selectList(
                new LambdaQueryWrapper<ParkingLane>()
                        .eq(ParkingLane::getParkingLotId, parkingLotId));

        // 3. 查询所有已绑定设备（按 laneId 分组）
        List<Long> laneIds = allLanes.stream().map(ParkingLane::getId).collect(Collectors.toList());
        Map<Long, List<Device>> devicesByLane = Collections.emptyMap();
        if (!laneIds.isEmpty()) {
            List<Device> allDevices = deviceMapper.selectList(
                    new LambdaQueryWrapper<Device>().in(Device::getLaneId, laneIds));
            devicesByLane = allDevices.stream()
                    .collect(Collectors.groupingBy(Device::getLaneId));
        }

        // 4. 分类车道
        List<ParkingLane> entryLanes = allLanes.stream()
                .filter(l -> ENTRY_DIRECTIONS.contains(l.getDirection()))
                .collect(Collectors.toList());
        List<ParkingLane> exitLanes = allLanes.stream()
                .filter(l -> EXIT_DIRECTIONS.contains(l.getDirection()))
                .collect(Collectors.toList());

        List<ReadinessItem> items = new ArrayList<>();

        // 5. 容量检查
        checkCapacity(lot, items);

        // 6. 车道存在性检查
        if (allLanes.isEmpty()) {
            items.add(ReadinessItem.blocker("NO_LANE", CAT_LANE, "停车场未配置任何车道"));
            return buildResult(lot, items);
        }
        if (entryLanes.isEmpty()) {
            items.add(ReadinessItem.blocker("NO_ENTRY_LANE", CAT_LANE,
                    "未配置入口车道（需要 ENTRY 或 MIXED 方向）"));
        }
        if (exitLanes.isEmpty()) {
            items.add(ReadinessItem.blocker("NO_EXIT_LANE", CAT_LANE,
                    "未配置出口车道（需要 EXIT 或 MIXED 方向）"));
        }

        // 7. 入口方向设备检查
        checkDirectionDevices(entryLanes, devicesByLane, "ENTRY", "入口", items);

        // 8. 出口方向设备检查
        checkDirectionDevices(exitLanes, devicesByLane, "EXIT", "出口", items);

        // 9. 占位项（未实现模块，明确标记 implemented=false）
        items.add(ReadinessItem.placeholder("CHARGE_RULE_NOT_CONFIGURED", CAT_CHARGE_RULE,
                "收费规则尚未配置（T34 待实现）"));
        items.add(ReadinessItem.placeholder("PAYMENT_NOT_CONFIGURED", CAT_PAYMENT,
                "支付配置尚未完成（T42 待实现）"));
        // 10. 设备在线状态检查（基于最新快照，T24 已实现）
        checkDeviceOnlineStatus(allLanes, devicesByLane, items);

        log.info("就绪检查完成: parkingLotId={}, name={}, blockers={}, warnings={}",
                parkingLotId, lot.getName(),
                items.stream().filter(i -> "BLOCKER".equals(i.getLevel())).count(),
                items.stream().filter(i -> "WARNING".equals(i.getLevel())).count());

        return buildResult(lot, items);
    }

    // ==================== 容量检查 ====================

    private void checkCapacity(ParkingLot lot, List<ReadinessItem> items) {
        Integer totalSpaces = lot.getTotalSpaces();
        if (totalSpaces == null || totalSpaces <= 0) {
            items.add(ReadinessItem.blocker("NO_TOTAL_SPACES", CAT_CAPACITY,
                    "未配置总车位数（totalSpaces 不能为空或 0）"));
        }
    }

    // ==================== 方向设备检查 ====================

    /**
     * 对一组同方向车道进行设备就绪检查。
     * <p>
     * 每条车道单独产生 WARNING 级检查项；
     * 如果该方向没有任何一条车道完全就绪，则产生 BLOCKER 级汇总检查项。
     *
     * @param lanes         该方向的车道列表（可能为空）
     * @param devicesByLane laneId → 已绑定设备
     * @param dirCode       方向编码（ENTRY / EXIT）
     * @param dirLabel      方向中文标签（入口 / 出口）
     * @param items         结果收集器
     */
    private void checkDirectionDevices(List<ParkingLane> lanes,
                                        Map<Long, List<Device>> devicesByLane,
                                        String dirCode, String dirLabel,
                                        List<ReadinessItem> items) {
        if (lanes.isEmpty()) {
            return; // 已在 lane existence check 中处理
        }

        boolean anyLaneReady = false;
        boolean anyHasCamera = false;
        boolean anyCameraEnabled = false;
        boolean anyHasGate = false;
        boolean anyGateEnabled = false;
        boolean anyGateHasExecutor = false;

        for (ParkingLane lane : lanes) {
            List<Device> laneDevices = devicesByLane.getOrDefault(lane.getId(), Collections.emptyList());

            List<Device> cameras = laneDevices.stream()
                    .filter(d -> DEVICE_TYPE_CAMERA.equals(d.getDeviceType()))
                    .collect(Collectors.toList());
            List<Device> gates = laneDevices.stream()
                    .filter(d -> DEVICE_TYPE_GATE.equals(d.getDeviceType()))
                    .collect(Collectors.toList());

            boolean hasCamera = !cameras.isEmpty();
            boolean cameraEnabled = hasCamera && cameras.stream().anyMatch(d -> STATUS_ENABLED.equals(d.getStatus()));
            boolean hasGate = !gates.isEmpty();
            boolean gateEnabled = hasGate && gates.stream().anyMatch(d -> STATUS_ENABLED.equals(d.getStatus()));
            boolean gateHasExecutor = gateEnabled && gates.stream()
                    .anyMatch(d -> d.getExecutorDeviceId() != null);

            // 单条车道 WARNING
            if (!hasCamera) {
                items.add(ReadinessItem.warning(dirCode + "_CAMERA_MISSING", CAT_DEVICE,
                        dirLabel + "车道 '" + lane.getName() + "' 未绑定相机", lane.getName()));
            } else if (!cameraEnabled) {
                items.add(ReadinessItem.warning(dirCode + "_CAMERA_DISABLED", CAT_DEVICE,
                        dirLabel + "车道 '" + lane.getName() + "' 的相机已停用", lane.getName()));
            }

            if (!hasGate) {
                items.add(ReadinessItem.warning(dirCode + "_GATE_MISSING", CAT_DEVICE,
                        dirLabel + "车道 '" + lane.getName() + "' 未绑定道闸", lane.getName()));
            } else if (!gateEnabled) {
                items.add(ReadinessItem.warning(dirCode + "_GATE_DISABLED", CAT_DEVICE,
                        dirLabel + "车道 '" + lane.getName() + "' 的道闸已停用", lane.getName()));
            } else if (!gateHasExecutor) {
                items.add(ReadinessItem.warning(dirCode + "_GATE_NO_EXECUTOR", CAT_DEVICE,
                        dirLabel + "车道 '" + lane.getName() + "' 的道闸未设置执行相机", lane.getName()));
            }

            // 汇总统计
            if (hasCamera) anyHasCamera = true;
            if (cameraEnabled) anyCameraEnabled = true;
            if (hasGate) anyHasGate = true;
            if (gateEnabled) anyGateEnabled = true;
            if (gateHasExecutor) anyGateHasExecutor = true;

            // 车道完全就绪 = 有启用相机 + 有启用道闸 + 道闸有执行相机
            boolean laneReady = hasCamera && cameraEnabled && hasGate && gateEnabled && gateHasExecutor;
            if (laneReady) {
                anyLaneReady = true;
            }
        }

        // 如果该方向没有任何一条车道完全就绪，产生 BLOCKER
        if (anyLaneReady) {
            return;
        }

        if (!anyHasCamera) {
            items.add(ReadinessItem.blocker(dirCode + "_CAMERA_MISSING", CAT_DEVICE,
                    "所有" + dirLabel + "车道均未绑定相机"));
        } else if (!anyCameraEnabled) {
            items.add(ReadinessItem.blocker(dirCode + "_CAMERA_DISABLED", CAT_DEVICE,
                    "所有" + dirLabel + "车道的相机均已停用"));
        }

        if (!anyHasGate) {
            items.add(ReadinessItem.blocker(dirCode + "_GATE_MISSING", CAT_DEVICE,
                    "所有" + dirLabel + "车道均未绑定道闸"));
        } else if (!anyGateEnabled) {
            items.add(ReadinessItem.blocker(dirCode + "_GATE_DISABLED", CAT_DEVICE,
                    "所有" + dirLabel + "车道的道闸均已停用"));
        } else if (!anyGateHasExecutor) {
            items.add(ReadinessItem.blocker(dirCode + "_GATE_NO_EXECUTOR", CAT_DEVICE,
                    "所有" + dirLabel + "车道的道闸均未设置执行相机"));
        }
    }

    // ==================== 设备在线状态检查（T24） ====================

    /**
     * 检查车道上所有已绑定设备的最新在线状态。
     * <p>
     * 基于最新持久化的状态快照判断设备在线情况。
     * 从未查询过状态的设备 → WARNING 提示"尚未查询过状态"。
     * 快照过期（超过阈值）→ WARNING 提示"状态信息可能已过期"。
     * 明确离线 → WARNING 提示"设备离线"。
     *
     * @param allLanes      所有车道
     * @param devicesByLane laneId → 已绑定设备
     * @param items         结果收集器
     */
    private void checkDeviceOnlineStatus(List<ParkingLane> allLanes,
                                         Map<Long, List<Device>> devicesByLane,
                                         List<ReadinessItem> items) {
        int totalDevices = 0;
        int onlineCount = 0;
        int offlineCount = 0;
        int unknownCount = 0;

        for (ParkingLane lane : allLanes) {
            List<Device> laneDevices = devicesByLane.getOrDefault(lane.getId(), Collections.emptyList());
            for (Device device : laneDevices) {
                if (!STATUS_ENABLED.equals(device.getStatus())) {
                    continue; // 已停用设备不检查在线状态
                }
                totalDevices++;

                DeviceStatusSnapshot snapshot = snapshotMapper.selectOne(
                        new LambdaQueryWrapper<DeviceStatusSnapshot>()
                                .eq(DeviceStatusSnapshot::getDeviceId, device.getId())
                                .orderByDesc(DeviceStatusSnapshot::getCollectedAt)
                                .last("LIMIT 1"));

                if (snapshot == null) {
                    unknownCount++;
                    items.add(ReadinessItem.warning("DEVICE_STATUS_NEVER_QUERIED", CAT_DEVICE_STATUS,
                            "设备 '" + device.getName() + "'（" + lane.getName() + "）尚未查询过在线状态", lane.getName()));
                    continue;
                }

                // 检查快照是否过期
                if (snapshot.getCollectedAt() != null) {
                    long ageSeconds = Duration.between(snapshot.getCollectedAt(), LocalDateTime.now()).getSeconds();
                    if (ageSeconds > SNAPSHOT_STALE_SECONDS) {
                        items.add(ReadinessItem.warning("DEVICE_STATUS_STALE", CAT_DEVICE_STATUS,
                                "设备 '" + device.getName() + "' 的状态信息已过期（" + ageSeconds + " 秒前采集）", lane.getName()));
                    }
                }

                // 检查在线状态
                if (Boolean.TRUE.equals(snapshot.getOnline())) {
                    onlineCount++;
                } else if (Boolean.FALSE.equals(snapshot.getOnline())) {
                    offlineCount++;
                    items.add(ReadinessItem.warning("DEVICE_OFFLINE", CAT_DEVICE_STATUS,
                            "设备 '" + device.getName() + "'（" + lane.getName() + "）当前离线", lane.getName()));
                } else {
                    unknownCount++;
                }
            }
        }

        log.info("设备在线状态检查完成: total={}, online={}, offline={}, unknown={}",
                totalDevices, onlineCount, offlineCount, unknownCount);
    }

    // ==================== 结果构造 ====================

    private ParkingLotReadinessVO buildResult(ParkingLot lot, List<ReadinessItem> items) {
        long blockerCount = items.stream().filter(i -> "BLOCKER".equals(i.getLevel())).count();
        long warningCount = items.stream().filter(i -> "WARNING".equals(i.getLevel())).count();

        ParkingLotReadinessVO vo = new ParkingLotReadinessVO();
        vo.setParkingLotId(lot.getId());
        vo.setParkingLotName(lot.getName());
        vo.setReady(blockerCount == 0);
        vo.setBlockerCount((int) blockerCount);
        vo.setWarningCount((int) warningCount);
        vo.setItems(items);
        return vo;
    }

    // ==================== 租户隔离 ====================

    /**
     * 查询停车场并校验租户归属（fail-close）。
     */
    private ParkingLot getParkingLotWithAuth(Long lotId) {
        ParkingLot lot = parkingLotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }
        DataScope.validateTenantMatch(lot.getTenantId(), "停车场");
        // P0：停车场级数据范围校验
        scopeResolver.validateAccess(lotId);
        return lot;
    }
}
