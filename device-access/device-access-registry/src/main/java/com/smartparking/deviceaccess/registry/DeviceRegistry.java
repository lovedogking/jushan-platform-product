package com.smartparking.deviceaccess.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceMapper;
import com.smartparking.deviceaccess.common.exception.DeviceAlreadyExistsException;
import com.smartparking.deviceaccess.common.exception.DeviceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备注册中心。
 * <p>
 * 负责设备元数据的 CRUD 操作，是 t_device 表的唯一写入入口。
 * 只依赖 common 模块（DeviceMapper + Device 实体），不依赖 MQTT 或 HTTP 层。
 * <p>
 * v0.2 新增，替代 v0.1 的 DeviceConfigLoader（配置文件同步）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRegistry {

    private final DeviceMapper deviceMapper;

    // ──────────────────── 注册 ────────────────────

    /**
     * 注册新设备。
     * <p>
     * deviceId 统一归一化为小写存储。插入前不做 check-then-insert，
     * 由数据库 UNIQUE KEY 兜底并发安全。
     *
     * @param device 待注册设备（至少设置 deviceId 和 deviceName）
     * @return 插入后的设备实体（含自增 ID 和自动填充的时间字段）
     * @throws DeviceAlreadyExistsException 如果 deviceId 已存在
     */
    public Device register(Device device) {
        // 归一化
        device.setDeviceId(device.getDeviceId().toLowerCase());

        // 默认值
        if (!StringUtils.hasText(device.getStatus())) {
            device.setStatus("OFFLINE");
        }

        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());

        try {
            deviceMapper.insert(device);
            log.info("Device registered: deviceId={}, name={}", device.getDeviceId(), device.getDeviceName());
            return device;
        } catch (DuplicateKeyException e) {
            throw new DeviceAlreadyExistsException(device.getDeviceId());
        }
    }

    // ──────────────────── 查询 ────────────────────

    /**
     * 按 deviceId 查询设备，不存在返回 null。
     */
    public Device findByDeviceId(String deviceId) {
        return deviceMapper.selectOne(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceId, deviceId.toLowerCase()));
    }

    /**
     * 按 deviceId 查询设备，不存在抛出 DeviceNotFoundException。
     */
    public Device getByDeviceId(String deviceId) {
        Device device = findByDeviceId(deviceId);
        if (device == null) {
            throw new DeviceNotFoundException(deviceId);
        }
        return device;
    }

    /**
     * 分页查询设备列表，支持多条件筛选。
     *
     * @param page       分页参数
     * @param keyword    模糊匹配 deviceId 或 deviceName（可选）
     * @param productIds 产品ID列表筛选（可选，null 表示不筛选）
     * @param direction  方向筛选（可选）
     * @param status     状态精确匹配（可选）
     * @return 分页结果
     */
    public IPage<Device> list(Page<Device> page, String keyword, List<Long> productIds,
                               String direction, String status) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Device::getDeviceId, keyword)
                    .or()
                    .like(Device::getDeviceName, keyword));
        }
        if (productIds != null && !productIds.isEmpty()) {
            wrapper.in(Device::getProductId, productIds);
        }
        if (StringUtils.hasText(direction)) {
            wrapper.eq(Device::getDirection, direction);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Device::getStatus, status);
        }

        wrapper.orderByDesc(Device::getCreateTime);

        return deviceMapper.selectPage(page, wrapper);
    }

    /**
     * 全量查询设备列表（不分页），支持多条件筛选。
     * <p>
     * v0.2 设备量少，暂不分页。v0.5+ 引入分页后此方法可移除。
     * v0.4: 新增 tenantId / parkingLotId / laneId 业务字段筛选。
     *
     * @param keyword       模糊匹配 deviceId 或 deviceName（可选）
     * @param productIds    产品ID列表筛选（可选，null 表示不筛选）
     * @param direction     方向筛选（可选）
     * @param status        状态精确匹配（可选）
     * @param tenantId      租户ID筛选（可选）
     * @param parkingLotId  停车场ID筛选（可选）
     * @param laneId        车道ID筛选（可选）
     * @return 设备列表
     */
    public List<Device> list(String keyword, List<Long> productIds,
                              String direction, String status,
                              String tenantId, String parkingLotId, String laneId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Device::getDeviceId, keyword)
                    .or()
                    .like(Device::getDeviceName, keyword));
        }
        if (productIds != null && !productIds.isEmpty()) {
            wrapper.in(Device::getProductId, productIds);
        }
        if (StringUtils.hasText(direction)) {
            wrapper.eq(Device::getDirection, direction);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Device::getStatus, status);
        }
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(Device::getTenantId, tenantId);
        }
        if (StringUtils.hasText(parkingLotId)) {
            wrapper.eq(Device::getParkingLotId, parkingLotId);
        }
        if (StringUtils.hasText(laneId)) {
            wrapper.eq(Device::getLaneId, laneId);
        }

        wrapper.orderByDesc(Device::getCreateTime);

        return deviceMapper.selectList(wrapper);
    }

    /**
     * 判断 deviceId 是否已被占用。
     */
    public boolean existsByDeviceId(String deviceId) {
        return deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceId, deviceId.toLowerCase())) > 0;
    }

    // ──────────────────── 更新 ────────────────────

    /**
     * 更新设备信息（部分更新）。
     * <p>
     * 只更新请求中提供的非 null 字段。deviceId 和 createTime 不可更新。
     *
     * @param deviceId     设备标识
     * @param updateFields 待更新字段（只取非 null 值）
     * @return 更新后的完整设备实体
     * @throws DeviceNotFoundException 如果设备不存在
     */
    public Device update(String deviceId, Device updateFields) {
        String normalizedId = deviceId.toLowerCase();
        Device existing = getByDeviceId(normalizedId);

        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getDeviceId, normalizedId);

        boolean hasUpdate = false;
        if (StringUtils.hasText(updateFields.getDeviceName())) {
            wrapper.set(Device::getDeviceName, updateFields.getDeviceName());
            hasUpdate = true;
        }
        if (StringUtils.hasText(updateFields.getDirection())) {
            wrapper.set(Device::getDirection, updateFields.getDirection());
            hasUpdate = true;
        }
        if (updateFields.getPlatformDeviceId() != null) {
            wrapper.set(Device::getPlatformDeviceId, updateFields.getPlatformDeviceId());
            hasUpdate = true;
        }
        if (updateFields.getTenantId() != null) {
            wrapper.set(Device::getTenantId, updateFields.getTenantId());
            hasUpdate = true;
        }
        if (updateFields.getParkingLotId() != null) {
            wrapper.set(Device::getParkingLotId, updateFields.getParkingLotId());
            hasUpdate = true;
        }
        if (updateFields.getLaneId() != null) {
            wrapper.set(Device::getLaneId, updateFields.getLaneId());
            hasUpdate = true;
        }
        if (updateFields.getRemark() != null) {
            wrapper.set(Device::getRemark, updateFields.getRemark());
            hasUpdate = true;
        }
        if (updateFields.getDisplayEnabled() != null) {
            wrapper.set(Device::getDisplayEnabled, updateFields.getDisplayEnabled());
            hasUpdate = true;
        }
        if (StringUtils.hasText(updateFields.getDisplayMode())) {
            wrapper.set(Device::getDisplayMode, updateFields.getDisplayMode());
            hasUpdate = true;
        }

        if (hasUpdate) {
            wrapper.set(Device::getUpdateTime, LocalDateTime.now());
            deviceMapper.update(null, wrapper);
            log.info("Device updated: deviceId={}", normalizedId);
        }

        // 重新查询返回最新数据
        return getByDeviceId(normalizedId);
    }

    // ──────────────────── 删除（软删除） ────────────────────

    /**
     * 软删除设备。
     * <p>
     * MyBatis Plus {@code @TableLogic} 自动将 deleteById 转换为 UPDATE deleted=1。
     *
     * @throws DeviceNotFoundException 如果设备不存在
     */
    public void deleteByDeviceId(String deviceId) {
        Device device = getByDeviceId(deviceId);
        deviceMapper.deleteById(device.getId());
        log.info("Device deleted (soft): deviceId={}", deviceId.toLowerCase());
    }
}
