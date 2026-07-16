package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.client.dto.CommandResultDTO;
import com.jushan.system.dto.RemoteGateAlertDTO;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.SysUser;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.service.DeviceService;
import com.jushan.system.service.ParkingLotScopeResolver;
import com.jushan.system.service.SystemParamService;
import com.jushan.system.ws.BoothWebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 远程开闸管理 Controller（Phase 1 B2）。
 * <p>
 * 提供运营端按车道远程开闸能力。
 * 开闸成功后通过 WebSocket 推送弹窗到岗亭端。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>需要 {@code device:remote:open} 权限</li>
 *   <li>租户管理员默认不拥有，需超管手动勾选</li>
 *   <li>车道和设备绑定关系从可信记录查询，禁止前端传入 deviceSn</li>
 *   <li>开闸失败不自动重试（UNCERTAIN 由人工处置）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/remote-gate")
public class RemoteGateController {

    private static final Logger log = LoggerFactory.getLogger(RemoteGateController.class);

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeviceService deviceService;
    private final ParkingLaneMapper laneMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final SysUserMapper userMapper;
    private final ParkingLotScopeResolver scopeResolver;
    private final BoothWebSocketPublisher wsPublisher;
    private final SystemParamService systemParamService;

    public RemoteGateController(DeviceService deviceService,
                                 ParkingLaneMapper laneMapper,
                                 ParkingLotMapper parkingLotMapper,
                                 SysUserMapper userMapper,
                                 ParkingLotScopeResolver scopeResolver,
                                 BoothWebSocketPublisher wsPublisher,
                                 SystemParamService systemParamService) {
        this.deviceService = deviceService;
        this.laneMapper = laneMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.userMapper = userMapper;
        this.scopeResolver = scopeResolver;
        this.wsPublisher = wsPublisher;
        this.systemParamService = systemParamService;
    }

    /**
     * 远程开闸。
     * <p>
     * POST /api/v1/admin/remote-gate/open
     * <p>
     * 1. 校验请求参数和停车场权限
     * 2. 通过 DeviceService.openGateByLane 执行开闸
     * 3. 开闸成功后推送 WebSocket 通知到岗亭端
     * 4. 记录操作日志
     *
     * @param body 请求体：{ parkingLotId, laneId, reason }
     * @return 开闸结果
     */
    @PostMapping("/open")
    @RequirePermission("device:remote:open")
    @BusinessLog(value = "远程开闸", module = "remote_gate", operationType = "UPDATE",
            operationObject = "车道")
    public R<CommandResultDTO> openGate(@RequestBody Map<String, Object> body) {
        // 1. 参数校验
        Long parkingLotId = body.get("parkingLotId") != null
                ? ((Number) body.get("parkingLotId")).longValue() : null;
        Long laneId = body.get("laneId") != null
                ? ((Number) body.get("laneId")).longValue() : null;
        String reason = body.get("reason") != null
                ? body.get("reason").toString().trim() : "";

        if (parkingLotId == null || laneId == null) {
            return R.fail(CommonErrorCode.PARAM_ERROR.getCode(), "车场ID和车道ID不能为空");
        }
        if (reason.isEmpty()) {
            return R.fail(CommonErrorCode.PARAM_ERROR.getCode(), "开闸原因不能为空");
        }

        // 2. 校验停车场访问权限
        try {
            scopeResolver.validateAccess(parkingLotId);
        } catch (BusinessException e) {
            return R.fail(CommonErrorCode.FORBIDDEN.getCode(), "无权操作该停车场");
        }

        // 3. 收集关联信息（用于 WebSocket 推送）
        ParkingLane lane = laneMapper.selectByIdIgnoreTenant(laneId);
        if (lane == null) {
            return R.fail(CommonErrorCode.NOT_FOUND.getCode(), "车道不存在");
        }
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(parkingLotId);
        String lotName = lot != null ? lot.getName() : String.valueOf(parkingLotId);
        String laneName = lane.getName() != null ? lane.getName() : lane.getLaneNo();

        // 操作人信息
        String operatorName = resolveOperatorName();

        // 4. 执行开闸
        CommandResultDTO result;
        try {
            result = deviceService.openGateByLane(laneId, reason);
        } catch (BusinessException e) {
            log.warn("远程开闸业务失败: parkingLotId={}, laneId={}, reason={}, error={}",
                    parkingLotId, laneId, reason, e.getMessage());
            return R.fail(e.getCode(), e.getMessage());
        }

        // 5. 推送 WebSocket 通知到岗亭
        if (result.isSuccessful()) {
            RemoteGateAlertDTO dto = new RemoteGateAlertDTO();
            dto.setOperatorName(operatorName);
            dto.setOperationTime(LocalDateTime.now().format(DTF));
            dto.setParkingLotName(lotName);
            dto.setLaneName(laneName);
            dto.setReason(reason);
            // 从系统参数读取弹窗自动消失秒数（默认 10）
            String dismissStr = systemParamService.getValue("remote_gate.alert_auto_dismiss_seconds");
            if (dismissStr != null) {
                try {
                    dto.setAutoDismissSeconds(Integer.parseInt(dismissStr));
                } catch (NumberFormatException e) {
                    log.warn("remote_gate.alert_auto_dismiss_seconds 格式非法: {}", dismissStr);
                }
            }

            wsPublisher.sendRemoteGateAlert(parkingLotId, dto);
        }

        log.info("远程开闸完成: parkingLotId={}, laneId={}, laneName={}, operator={}, reason={}, success={}",
                parkingLotId, laneId, laneName, operatorName, reason, result.isSuccessful());

        return R.ok(result);
    }

    /**
     * 获取当前操作人显示名称。
     */
    private String resolveOperatorName() {
        try {
            Long userId = TenantContext.getUserId();
            if (userId != null) {
                SysUser user = userMapper.selectById(userId);
                if (user != null) {
                    return user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
                }
            }
        } catch (Exception e) {
            log.warn("获取操作人名称失败: {}", e.getMessage());
        }
        return String.valueOf(TenantContext.getUserId());
    }
}
