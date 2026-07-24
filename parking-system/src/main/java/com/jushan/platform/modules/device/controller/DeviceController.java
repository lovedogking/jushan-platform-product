// @Deprecated — 迁移至 com.jushan.platform.modules.device.controller.DeviceController
package com.jushan.platform.modules.device.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.infra.security.RequireRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.modules.device.dto.CreateDeviceRequest;
import com.jushan.platform.modules.device.dto.UpdateDeviceRequest;
import com.jushan.platform.modules.device.entity.DeviceModel;
import com.jushan.platform.modules.device.entity.DeviceVendor;
import com.jushan.platform.modules.device.service.DeviceService;
import com.jushan.platform.modules.device.client.dto.CommandResultDTO;
import com.jushan.platform.modules.device.client.dto.DisplayResultDTO;
import com.jushan.platform.modules.device.client.dto.TimeSyncResultDTO;
import com.jushan.platform.modules.device.client.dto.VoiceResultDTO;
import com.jushan.platform.modules.device.vo.DeviceStatusVO;
import com.jushan.platform.modules.device.vo.DeviceVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 平台设备台账管理控制器（T20）。
 * <p>
 * 客户管理员和停车场管理员通过此接口管理本租户内停车场的设备。
 * 所有操作通过停车场归属推导租户范围，不信任前端传入的 tenantId。
 * 前端只能提交平台设备引用（vendorId/modelId/parkingLotId），禁止直接传入 device_sn。
 * <p>
 * <strong>权限</strong>：
 * <ul>
 *   <li>{@code device:read} — 查看设备列表、详情、厂商/型号</li>
 *   <li>{@code device:manage} — 创建/编辑设备、启用/停用</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/devices")
@RequireRole("platform") // SA-03: 仅超管（V1.4 权限矩阵修正）
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // ==================== 设备 CRUD ====================

    /**
     * 分页查询设备列表。
     * <p>
     * 权限：device:read
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param parkingLotId 停车场 ID（必填）
     * @param status       状态筛选（可选：ENABLED / DISABLED）
     * @param deviceType   设备类型筛选（可选：CAMERA / GATE）
     */
    @GetMapping
    @RequirePermission("device:read")
    public R<IPage<DeviceVO>> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) Long parkingLotId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String deviceType) {
        IPage<DeviceVO> result = deviceService.list(page, size, parkingLotId, status, deviceType);
        return R.ok(result);
    }

    /**
     * 查询设备详情。
     * <p>
     * 权限：device:read
     */
    @GetMapping("/{id}")
    @RequirePermission("device:read")
    public R<DeviceVO> detail(@PathVariable Long id) {
        DeviceVO vo = deviceService.get(id);
        return R.ok(vo);
    }

    /**
     * 创建设备。
     * <p>
     * 权限：device:manage
     */
    @PostMapping
    @RequirePermission("device:manage")
    public R<DeviceVO> create(@Valid @RequestBody CreateDeviceRequest request) {
        DeviceVO vo = deviceService.create(request);
        log.info("创建设备成功: deviceId={}, parkingLotId={}, name={}, code={}",
                vo.getId(), vo.getParkingLotId(), vo.getName(), vo.getCode());
        return R.ok(vo);
    }

    /**
     * 更新设备基础信息（部分更新）。
     * <p>
     * 权限：device:manage
     */
    @PutMapping("/{id}")
    @RequirePermission("device:manage")
    public R<DeviceVO> update(@PathVariable Long id, @Valid @RequestBody UpdateDeviceRequest request) {
        DeviceVO vo = deviceService.update(id, request);
        log.info("更新设备成功: deviceId={}", id);
        return R.ok(vo);
    }

    /**
     * 物理删除设备（不保留停用状态）。
     * <p>
     * 权限：device:manage
     */
    @DeleteMapping("/{id}")
    @RequirePermission("device:manage")
    public R<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        log.info("设备删除成功: deviceId={}", id);
        return R.ok();
    }

    /**
     * 启用或停用设备。
     * <p>
     * 权限：device:manage
     *
     * @param id   设备 ID
     * @param body 包含 action 字段：ENABLED / DISABLED
     */
    @PostMapping("/{id}/status")
    @RequirePermission("device:manage")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String action = body.get("action");
        deviceService.updateStatus(id, action);
        log.info("设备状态变更成功: deviceId={}, action={}", id, action);
        return R.ok();
    }

    // ==================== 车道绑定（T21） ====================

    /**
     * 将设备绑定到车道。
     * <p>
     * 权限：device:manage
     *
     * @param id   设备 ID
     * @param body 包含 laneId 字段
     */
    @PostMapping("/{id}/bind-lane")
    @RequirePermission("device:manage")
    public R<DeviceVO> bindLane(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long laneId = body.get("laneId");
        if (laneId == null) {
            return R.fail(1000, "车道 ID 不能为空");
        }
        DeviceVO vo = deviceService.bindLane(id, laneId);
        log.info("设备绑定车道成功: deviceId={}, laneId={}", id, laneId);
        return R.ok(vo);
    }

    /**
     * 将设备与车道解绑。
     * <p>
     * 权限：device:manage
     */
    @DeleteMapping("/{id}/bind-lane")
    @RequirePermission("device:manage")
    public R<DeviceVO> unbindLane(@PathVariable Long id) {
        DeviceVO vo = deviceService.unbindLane(id);
        log.info("设备解绑车道成功: deviceId={}", id);
        return R.ok(vo);
    }

    /**
     * 为 GATE 设备设置执行相机。
     * <p>
     * 权限：device:manage
     *
     * @param id    GATE 设备 ID
     * @param body 包含 executorDeviceId 字段
     */
    @PostMapping("/{id}/executor")
    @RequirePermission("device:manage")
    public R<DeviceVO> setExecutor(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long executorDeviceId = body.get("executorDeviceId");
        if (executorDeviceId == null) {
            return R.fail(1000, "执行设备 ID 不能为空");
        }
        DeviceVO vo = deviceService.setExecutor(id, executorDeviceId);
        log.info("设置 GATE 执行相机成功: gateDeviceId={}, executorDeviceId={}", id, executorDeviceId);
        return R.ok(vo);
    }

    // ==================== 厂商/型号 ====================

    /**
     * 查询所有启用厂商列表。
     * <p>
     * 权限：device:read
     */
    @GetMapping("/vendors")
    @RequirePermission("device:read")
    public R<List<DeviceVendor>> listVendors() {
        List<DeviceVendor> vendors = deviceService.listVendors();
        return R.ok(vendors);
    }

    /**
     * 查询型号列表（可选按厂商筛选）。
     * <p>
     * 权限：device:read
     *
     * @param vendorId 厂商 ID（可选）
     */
    @GetMapping("/models")
    @RequirePermission("device:read")
    public R<List<DeviceModel>> listModels(@RequestParam(required = false) Long vendorId) {
        List<DeviceModel> models = deviceService.listModels(vendorId);
        return R.ok(models);
    }

    // ==================== 设备校时（T25） ====================

    /**
     * 设备校时（调用 Device Access）。
     * <p>
     * 向 Device Access 发送校时命令，持久化审计记录。
     * 当前 DA v0.2 无 commandId 幂等，每次调用都会向设备发送一次校时命令。
     * <b>不包含任何自动重试</b>，超时或网络中断标记为 UNCERTAIN。
     * <p>
     * 权限：device:manage
     *
     * @param id   平台设备 ID
     * @param body 包含 reason 字段（操作原因）
     */
    @PostMapping("/{id}/sync-time")
    @RequirePermission("device:manage")
    public R<TimeSyncResultDTO> syncTime(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        TimeSyncResultDTO result = deviceService.syncTime(id, reason);
        log.info("设备校时完成: deviceId={}, success={}", id, result.isSuccessful());
        return R.ok(result);
    }

    // ==================== 设备控制（T5: v0.4 开闸/关闸/显示屏/语音） ====================

    /**
     * 开闸（调用 Device Access v0.4）。
     * <p>
     * 写操作，<b>禁止自动重试</b>。网络超时标记为 UNCERTAIN。
     * <p>
     * 权限：device:manage
     *
     * @param id   平台设备 ID
     * @param body 包含 reason 字段（操作原因，可选）
     */
    @PostMapping("/{id}/open-gate")
    @RequirePermission("device:manage")
    public R<CommandResultDTO> openGate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        CommandResultDTO result = deviceService.openGate(id, reason);
        log.info("开闸完成: deviceId={}, success={}", id, result.isSuccessful());
        return R.ok(result);
    }

    /**
     * 关闸（调用 Device Access v0.4）。
     * <p>
     * 写操作，<b>禁止自动重试</b>。网络超时标记为 UNCERTAIN。
     * <p>
     * 权限：device:manage
     *
     * @param id   平台设备 ID
     * @param body 包含 reason 字段（操作原因，可选）
     */
    @PostMapping("/{id}/close-gate")
    @RequirePermission("device:manage")
    public R<CommandResultDTO> closeGate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        CommandResultDTO result = deviceService.closeGate(id, reason);
        log.info("关闸完成: deviceId={}, success={}", id, result.isSuccessful());
        return R.ok(result);
    }

    /**
     * 显示屏实时文字（调用 Device Access v0.4）。
     * <p>
     * 权限：device:manage
     *
     * @param id   平台设备 ID
     * @param body 包含 content、direction、fontSize、color 字段
     */
    @PostMapping("/{id}/display-text")
    @RequirePermission("device:manage")
    public R<DisplayResultDTO> displayText(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        String direction = (String) body.getOrDefault("direction", "HORIZONTAL");
        Integer fontSize = body.get("fontSize") != null ? ((Number) body.get("fontSize")).intValue() : null;
        String color = (String) body.getOrDefault("color", "RED");
        if (content == null || content.isBlank()) {
            return R.fail(400, "显示内容不能为空");
        }
        DisplayResultDTO result = deviceService.displayText(id, content, direction, fontSize, color);
        log.info("显示屏文字完成: deviceId={}, success={}", id, result.getSuccess());
        return R.ok(result);
    }

    /**
     * 显示屏配置（音量/亮度/时间同步）（调用 Device Access v0.4）。
     * <p>
     * 权限：device:manage
     *
     * @param id   平台设备 ID
     * @param body 包含 configType、intValue、stringValue 字段
     */
    @PostMapping("/{id}/display-config")
    @RequirePermission("device:manage")
    public R<DisplayResultDTO> displayConfig(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String configType = (String) body.get("configType");
        Integer intValue = body.get("intValue") != null ? ((Number) body.get("intValue")).intValue() : null;
        String stringValue = (String) body.get("stringValue");
        if (configType == null || configType.isBlank()) {
            return R.fail(400, "配置类型不能为空");
        }
        DisplayResultDTO result = deviceService.displayConfig(id, configType, intValue, stringValue);
        log.info("显示屏配置完成: deviceId={}, configType={}, success={}", id, configType, result.getSuccess());
        return R.ok(result);
    }

    /**
     * 语音播报（调用 Device Access v0.4）。
     * <p>
     * 权限：device:manage
     *
     * @param id   平台设备 ID
     * @param body 包含 action、voiceId、variable 字段
     */
    @PostMapping("/{id}/voice-control")
    @RequirePermission("device:manage")
    public R<VoiceResultDTO> voiceControl(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String action = (String) body.getOrDefault("action", "PLAY");
        Integer voiceId = body.get("voiceId") != null ? ((Number) body.get("voiceId")).intValue() : null;
        String variable = (String) body.get("variable");
        VoiceResultDTO result = deviceService.voiceControl(id, action, voiceId, variable);
        log.info("语音播报完成: deviceId={}, action={}, voiceId={}, success={}",
                id, action, voiceId, result.getSuccess());
        return R.ok(result);
    }

    // ==================== 设备状态查询与快照（T24） ====================

    /**
     * 查询单个设备实时状态（调用 Device Access）。
     * <p>
     * 此操作会向 Device Access 发起 HTTP 请求，有网络开销，
     * 应仅在用户主动点击"刷新状态"时调用。
     * 每次调用都会持久化状态快照。
     * <p>
     * 权限：device:manage
     *
     * @param id 平台设备 ID
     */
    @PostMapping("/{id}/query-status")
    @RequirePermission("device:manage")
    public R<DeviceStatusVO> queryStatus(@PathVariable Long id) {
        DeviceStatusVO vo = deviceService.queryStatus(id);
        log.info("设备状态查询完成: deviceId={}, online={}, querySuccess={}",
                id, vo.getOnline(), vo.getLastQuerySuccess());
        return R.ok(vo);
    }

    /**
     * 批量查询设备实时状态。
     * <p>
     * 最多同时查询 5 台设备（信号量限流），每个设备独立查询并持久化快照。
     * 单个失败不影响其他设备。
     * <p>
     * 权限：device:manage
     *
     * @param body 包含 deviceIds 数组
     */
    @PostMapping("/query-status-batch")
    @RequirePermission("device:manage")
    public R<List<DeviceStatusVO>> queryStatusBatch(@RequestBody Map<String, List<Long>> body) {
        List<Long> deviceIds = body.get("deviceIds");
        if (deviceIds == null || deviceIds.isEmpty()) {
            return R.fail(400, "deviceIds 不能为空");
        }
        List<DeviceStatusVO> results = deviceService.queryStatusBatch(deviceIds);
        return R.ok(results);
    }

    /**
     * 获取设备最新状态快照（不调用 DA，仅读取最近持久化的快照）。
     * <p>
     * 返回结果中的 {@code stale} 字段指示快照是否已过期（超过 60 秒），
     * 前端应据此显示"最后查询于 X 秒前"。
     * 若设备从未查询过状态，所有状态字段均为 null。
     * <p>
     * 权限：device:read
     *
     * @param id 平台设备 ID
     */
    @GetMapping("/{id}/status")
    @RequirePermission("device:read")
    public R<DeviceStatusVO> getStatusSnapshot(@PathVariable Long id) {
        DeviceStatusVO vo = deviceService.getLatestSnapshot(id);
        return R.ok(vo);
    }

    /**
     * 批量获取设备最新状态快照。
     * <p>
     * 权限：device:read
     *
     * @param body 包含 deviceIds 数组
     */
    @PostMapping("/status-snapshots")
    @RequirePermission("device:read")
    public R<List<DeviceStatusVO>> getStatusSnapshots(@RequestBody Map<String, List<Long>> body) {
        List<Long> deviceIds = body.get("deviceIds");
        if (deviceIds == null || deviceIds.isEmpty()) {
            return R.fail(400, "deviceIds 不能为空");
        }
        List<DeviceStatusVO> results = deviceService.getLatestSnapshots(deviceIds);
        return R.ok(results);
    }

    // ==================== 车道相机配置辅助 ====================

    /**
     * 查询指定停车场下可绑定到车道的相机列表（含识别方向）。
     * <p>
     * 返回所有已启用且设备类型为 CAMERA 的设备，
     * 每项包含 deviceId、deviceName、recognitionDirection。
     * <p>
     * 权限：device:read
     *
     * @param parkingLotId 停车场 ID（必填）
     */
    @GetMapping("/available-for-lane")
    @RequirePermission("device:read")
    public R<List<Map<String, Object>>> availableForLane(@RequestParam Long parkingLotId) {
        List<Map<String, Object>> cameras = deviceService.listAvailableForLane(parkingLotId);
        return R.ok(cameras);
    }
}
