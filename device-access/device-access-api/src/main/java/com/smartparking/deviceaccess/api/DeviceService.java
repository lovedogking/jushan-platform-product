package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.api.dto.*;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.entity.DeviceRelation;
import com.smartparking.deviceaccess.common.enums.DeviceCapability;
import com.smartparking.deviceaccess.common.exception.CapabilityUnsupportedException;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import com.smartparking.deviceaccess.registry.DeviceRegistry;
import com.smartparking.deviceaccess.registry.DeviceRelationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 设备服务。
 * <p>
 * 编排 API 层和 adapter 层之间的调用。
 * 命令执行编排委托给 {@link ZhenshiDeviceCoordinator}。
 * v0.3: 适配 product 模型 + 新增设备关系管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final BrandCommandDispatcher brandDispatcher;
    private final DeviceRegistry deviceRegistry;
    private final DeviceProductRegistry productRegistry;
    private final DeviceRelationRegistry relationRegistry;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 设备与产品组合（内部辅助）。
     */
    private record DeviceWithProduct(Device device, DeviceProduct product) {
    }

    /**
     * 查询设备及其关联产品。
     */
    private DeviceWithProduct getDeviceWithProduct(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());
        return new DeviceWithProduct(device, product);
    }

    /**
     * 校验设备是否具备指定能力，不具备则抛出异常。
     */
    private void requireCapability(String deviceId, DeviceProduct product, DeviceCapability capability) {
        if (!product.hasCapability(capability)) {
            throw new CapabilityUnsupportedException(deviceId, capability);
        }
    }

    // ═══════════════════════════════════════════
    // v0.1 遗留：校时 / 状态查询
    // ═══════════════════════════════════════════

    /**
     * 向指定设备下发校时命令。
     */
    public CompletableFuture<CommandResultDTO> syncTime(String deviceId) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.TIME_SYNC);
        return brandDispatcher.syncTime(deviceId, dp.product());
    }

    /**
     * 开闸命令。
     */
    public CompletableFuture<CommandResultDTO> openGate(String deviceId) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.OPEN_GATE);
        return brandDispatcher.openGate(deviceId, dp.product());
    }

    /**
     * 关闸命令。
     */
    public CompletableFuture<CommandResultDTO> closeGate(String deviceId) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.CLOSE_GATE);
        return brandDispatcher.closeGate(deviceId, dp.product());
    }

    /**
     * 锁定道闸命令（锁定开闸，继电器强制吸合保持道闸开启）。
     */
    public CompletableFuture<CommandResultDTO> lockGate(String deviceId, LockGateRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.LOCK_OPEN_GATE);
        return brandDispatcher.lockGate(deviceId, dp.product(), req);
    }

    /**
     * 解除道闸锁定命令（解锁 IO0 + 重置状态机 + 关闸）。
     */
    public CompletableFuture<CommandResultDTO> unlockGate(String deviceId, UnlockGateRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.LOCK_OPEN_GATE);
        return brandDispatcher.unlockGate(deviceId, dp.product(), req);
    }

    /**
     * 查询设备状态。
     * <p>
     * 在线状态基于心跳缓存判定（臻识 30s 阈值，信路通 90s 阈值）。
     */
    public DeviceStatusDTO getStatus(String deviceId) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        boolean online = brandDispatcher.isDeviceOnline(dp.device().getDeviceId(), dp.product());

        return DeviceStatusDTO.builder()
                .deviceId(dp.device().getDeviceId())
                .deviceName(dp.device().getDeviceName())
                .brand(dp.product().getBrand())
                .model(dp.product().getModel())
                .online(online)
                .lastOnlineTime(dp.device().getLastOnlineTime() != null
                        ? dp.device().getLastOnlineTime().format(DATE_FMT)
                        : null)
                .build();
    }

    // ═══════════════════════════════════════════
    // v0.3: 设备 CRUD
    // ═══════════════════════════════════════════

    public DeviceDTO register(DeviceRegisterRequest req) {
        Device device = new Device();
        device.setDeviceId(req.getDeviceId());
        device.setDeviceName(req.getDeviceName());
        device.setProductId(req.getProductId());
        device.setDirection(req.getDirection());
        device.setPlatformDeviceId(req.getPlatformDeviceId());
        device.setTenantId(req.getTenantId());
        device.setParkingLotId(req.getParkingLotId());
        device.setLaneId(req.getLaneId());
        device.setRemark(req.getRemark());

        Device saved = deviceRegistry.register(device);
        DeviceProduct product = productRegistry.getById(saved.getProductId());
        return toDTO(saved, product);
    }

    public List<DeviceDTO> listDevices(String keyword, String deviceType,
                                        String direction, String status,
                                        String tenantId, String parkingLotId, String laneId) {
        List<Long> productIds = null;
        if (org.springframework.util.StringUtils.hasText(deviceType)) {
            List<DeviceProduct> products = productRegistry.listByDeviceType(deviceType);
            if (products.isEmpty()) {
                return Collections.emptyList();
            }
            productIds = products.stream().map(DeviceProduct::getId).toList();
        }

        List<Device> devices = deviceRegistry.list(keyword, productIds, direction, status,
                tenantId, parkingLotId, laneId);

        Map<Long, DeviceProduct> productMap = productRegistry.mapByIds(
                devices.stream().map(Device::getProductId).distinct().toList());

        return devices.stream()
                .map(d -> toDTO(d, productMap.get(d.getProductId())))
                .collect(Collectors.toList());
    }

    public DeviceDetailDTO getDeviceDetail(String deviceId) {
        Device device = deviceRegistry.getByDeviceId(deviceId);
        DeviceProduct product = productRegistry.getById(device.getProductId());

        List<DeviceRelation> relations = relationRegistry.getRelations(deviceId);
        List<DeviceRelationDTO> relationDTOs = relations.stream()
                .map(r -> toRelationDTO(r, deviceId))
                .collect(Collectors.toList());

        return toDetailDTO(device, product, relationDTOs);
    }

    public DeviceDTO updateDevice(String deviceId, DeviceUpdateRequest req) {
        Device updateFields = new Device();
        updateFields.setDeviceName(req.getDeviceName());
        updateFields.setDirection(req.getDirection());
        updateFields.setPlatformDeviceId(req.getPlatformDeviceId());
        updateFields.setTenantId(req.getTenantId());
        updateFields.setParkingLotId(req.getParkingLotId());
        updateFields.setLaneId(req.getLaneId());
        updateFields.setRemark(req.getRemark());
        updateFields.setDisplayEnabled(req.getDisplayEnabled());
        updateFields.setDisplayMode(req.getDisplayMode());

        Device updated = deviceRegistry.update(deviceId, updateFields);
        DeviceProduct product = productRegistry.getById(updated.getProductId());
        return toDTO(updated, product);
    }

    public void deleteDevice(String deviceId) {
        deviceRegistry.deleteByDeviceId(deviceId);
    }

    // ═══════════════════════════════════════════
    // v0.3: 设备关系管理
    // ═══════════════════════════════════════════

    public List<DeviceRelationDTO> getRelations(String deviceId) {
        List<DeviceRelation> relations = relationRegistry.getRelations(deviceId);
        return relations.stream()
                .map(r -> toRelationDTO(r, deviceId))
                .collect(Collectors.toList());
    }

    public DeviceRelationDTO createRelation(String deviceId, DeviceRelationRequest req) {
        DeviceRelation relation = relationRegistry.create(
                deviceId, req.getRelationType(), req.getTargetDeviceId(), req.getRemark());
        return toRelationDTO(relation, deviceId);
    }

    public void deleteRelation(String deviceId, Long relationId) {
        relationRegistry.delete(relationId);
    }

    public void enableRelation(String deviceId, Long relationId) {
        relationRegistry.enable(relationId);
    }

    public void disableRelation(String deviceId, Long relationId) {
        relationRegistry.disable(relationId);
    }

    // ═══════════════════════════════════════════
    // v0.3: 命令执行 → 委托给 Coordinator
    // ═══════════════════════════════════════════

    public CompletableFuture<PeripheralControlResult> controlPeripheral(String deviceId, PeripheralControlRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.PERIPHERAL_CONTROL);
        return brandDispatcher.controlPeripheral(deviceId, dp.product(), req);
    }

    public CompletableFuture<DisplayResult> displayText(String deviceId, DisplayTextRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.DISPLAY_TEXT);
        return brandDispatcher.displayText(deviceId, dp.product(), req.getContent(), req.getDirection());
    }

    public CompletableFuture<DisplayResult> saveDisplay(String deviceId, DisplaySaveRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.DISPLAY_SAVE);
        return brandDispatcher.saveDisplay(deviceId, dp.product(), req.getContent(), req.getDirection());
    }

    /**
     * 显示屏配置（音量、亮度、方向、时间同步）。
     */
    public CompletableFuture<DisplayResult> configDisplay(String deviceId, DisplayConfigRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.DISPLAY_CONFIG);
        return brandDispatcher.configDisplay(deviceId, dp.product(), req);
    }

    /**
     * 语音控制（播放、停止）。
     */
    public CompletableFuture<VoiceControlResult> controlVoice(String deviceId, VoiceControlRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.VOICE_CONTROL);
        return brandDispatcher.controlVoice(deviceId, dp.product(), req);
    }

    /**
     * 增强版实时显示文字。
     */
    public CompletableFuture<DisplayResult> displayTextEnhanced(String deviceId, DisplayTextRequest req) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.DISPLAY_ENHANCED);
        return brandDispatcher.displayTextEnhanced(
                deviceId, dp.product(), req.getContent(), req.getDirection(),
                req.getFont(), req.getColor(), req.getVoiceId(), req.getVoiceVariable());
    }

    /**
     * 主动抓拍。
     * <p>
     * 触发相机立即抓拍一张图片，保存到存储后返回可访问 URL。
     *
     * @param deviceId 设备 ID（注册时的 deviceId）
     * @return 抓拍结果（含图片 URL）
     * @since v0.7
     */
    public CompletableFuture<CaptureResultDTO> capture(String deviceId) {
        DeviceWithProduct dp = getDeviceWithProduct(deviceId);
        requireCapability(deviceId, dp.product(), DeviceCapability.CAPTURE);
        return brandDispatcher.capture(deviceId, dp.product());
    }

    // ═══════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════

    private DeviceDTO toDTO(Device d, DeviceProduct p) {
        DeviceDTO.DeviceDTOBuilder builder = DeviceDTO.builder()
                .deviceId(d.getDeviceId())
                .deviceName(d.getDeviceName())
                .direction(d.getDirection())
                .platformDeviceId(d.getPlatformDeviceId())
                .tenantId(d.getTenantId())
                .parkingLotId(d.getParkingLotId())
                .laneId(d.getLaneId())
                .displayEnabled(d.getDisplayEnabled())
                .displayMode(d.getDisplayMode())
                .status(d.getStatus())
                .remark(d.getRemark())
                .lastOnlineTime(d.getLastOnlineTime() != null
                        ? d.getLastOnlineTime().format(DATE_FMT) : null)
                .createTime(d.getCreateTime() != null
                        ? d.getCreateTime().format(DATE_FMT) : null)
                .updateTime(d.getUpdateTime() != null
                        ? d.getUpdateTime().format(DATE_FMT) : null);

        if (p != null) {
            builder.productId(p.getId())
                   .brand(p.getBrand())
                   .model(p.getModel())
                   .productName(p.getProductName())
                   .deviceType(p.getDeviceType())
                   .protocol(p.getProtocol())
                   .capabilities(p.getCapabilityList());
        }

        return builder.build();
    }

    private DeviceDetailDTO toDetailDTO(Device d, DeviceProduct p, List<DeviceRelationDTO> relations) {
        DeviceDetailDTO.DeviceDetailDTOBuilder builder = DeviceDetailDTO.builder()
                .deviceId(d.getDeviceId())
                .deviceName(d.getDeviceName())
                .direction(d.getDirection())
                .platformDeviceId(d.getPlatformDeviceId())
                .tenantId(d.getTenantId())
                .parkingLotId(d.getParkingLotId())
                .laneId(d.getLaneId())
                .displayEnabled(d.getDisplayEnabled())
                .displayMode(d.getDisplayMode())
                .status(d.getStatus())
                .remark(d.getRemark())
                .relations(relations)
                .lastOnlineTime(d.getLastOnlineTime() != null
                        ? d.getLastOnlineTime().format(DATE_FMT) : null)
                .createTime(d.getCreateTime() != null
                        ? d.getCreateTime().format(DATE_FMT) : null)
                .updateTime(d.getUpdateTime() != null
                        ? d.getUpdateTime().format(DATE_FMT) : null);

        if (p != null) {
            builder.productId(p.getId())
                   .brand(p.getBrand())
                   .model(p.getModel())
                   .productName(p.getProductName())
                   .deviceType(p.getDeviceType())
                   .protocol(p.getProtocol())
                   .capabilities(p.getCapabilityList());
        }

        return builder.build();
    }

    private DeviceRelationDTO toRelationDTO(DeviceRelation r, String currentDeviceId) {
        Device relatedDevice = r.getTargetDevice();
        if (relatedDevice == null) {
            return null;
        }

        String direction;
        String relatedDeviceId;
        if (currentDeviceId.equals(r.getSourceDeviceId())) {
            direction = "OUTBOUND";
            relatedDeviceId = r.getTargetDeviceId();
        } else {
            direction = "INBOUND";
            relatedDeviceId = r.getSourceDeviceId();
        }

        DeviceProduct relatedProduct = null;
        if (relatedDevice.getProductId() != null) {
            relatedProduct = productRegistry.getById(relatedDevice.getProductId());
        }

        return DeviceRelationDTO.builder()
                .relationId(r.getId())
                .relationType(r.getRelationType())
                .direction(direction)
                .relatedDeviceId(relatedDeviceId)
                .relatedDeviceName(relatedDevice.getDeviceName())
                .relatedProductName(relatedProduct != null ? relatedProduct.getProductName() : null)
                .relatedDeviceType(relatedProduct != null ? relatedProduct.getDeviceType() : null)
                .relatedStatus(relatedDevice.getStatus())
                .enabled(r.getEnabled())
                .remark(r.getRemark())
                .createTime(r.getCreateTime() != null
                        ? r.getCreateTime().format(DATE_FMT) : null)
                .build();
    }
}
