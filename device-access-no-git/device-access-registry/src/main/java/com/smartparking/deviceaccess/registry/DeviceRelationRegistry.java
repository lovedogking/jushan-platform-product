package com.smartparking.deviceaccess.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.deviceaccess.common.entity.Device;
import com.smartparking.deviceaccess.common.entity.DeviceRelation;
import com.smartparking.deviceaccess.common.entity.DeviceRelationMapper;
import com.smartparking.deviceaccess.common.exception.DeviceNotFoundException;
import com.smartparking.deviceaccess.common.exception.InvalidRelationException;
import com.smartparking.deviceaccess.common.exception.RelationAlreadyExistsException;
import com.smartparking.deviceaccess.common.exception.RelationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备关系注册中心。
 * <p>
 * 负责设备间业务关联的 CRUD 和启用/停用操作。
 * 不创建反向记录 — 双向查看通过分别查询 source 和 target 侧实现。
 * v0.3 新增。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRelationRegistry {

    private final DeviceRelationMapper relationMapper;
    private final DeviceRegistry deviceRegistry;
    private final DeviceProductRegistry productRegistry;

    // ──────────────────── 创建 ────────────────────

    /**
     * 创建设备关系。
     *
     * @param sourceDeviceId  关系源设备ID
     * @param relationType    关系类型
     * @param targetDeviceId  关系目标设备ID
     * @param remark          备注
     * @return 创建后的关系实体
     */
    public DeviceRelation create(String sourceDeviceId, String relationType,
                                  String targetDeviceId, String remark) {
        // 校验1: 不能关联自己
        if (sourceDeviceId.equals(targetDeviceId)) {
            throw new InvalidRelationException("Cannot relate a device to itself");
        }

        // 校验2: 双方设备必须存在
        Device source = deviceRegistry.findByDeviceId(sourceDeviceId);
        if (source == null) {
            throw new DeviceNotFoundException(sourceDeviceId);
        }
        Device target = deviceRegistry.findByDeviceId(targetDeviceId);
        if (target == null) {
            throw new DeviceNotFoundException(targetDeviceId);
        }

        // 校验3: 设备类型匹配
        var sourceProduct = productRegistry.getById(source.getProductId());
        var targetProduct = productRegistry.getById(target.getProductId());
        if ("AUX_CAMERA".equals(relationType)
                && (!"CAMERA".equals(sourceProduct.getDeviceType())
                    || !"CAMERA".equals(targetProduct.getDeviceType()))) {
            throw new InvalidRelationException(
                    "AUX_CAMERA relation requires both devices to be CAMERA type");
        }
        if ("RS485_DISPLAY".equals(relationType)
                && !"DISPLAY".equals(targetProduct.getDeviceType())) {
            throw new InvalidRelationException(
                    "RS485_DISPLAY relation requires target device to be DISPLAY type");
        }

        DeviceRelation relation = new DeviceRelation();
        relation.setSourceDeviceId(sourceDeviceId);
        relation.setTargetDeviceId(targetDeviceId);
        relation.setRelationType(relationType);
        relation.setEnabled(true);
        relation.setRemark(remark);
        relation.setCreateTime(LocalDateTime.now());
        relation.setUpdateTime(LocalDateTime.now());

        try {
            relationMapper.insert(relation);
            log.info("Relation created: {} {} → {}, id={}",
                    relationType, sourceDeviceId, targetDeviceId, relation.getId());
            // 填充 target 设备信息用于返回
            relation.setTargetDevice(target);
            return relation;
        } catch (DuplicateKeyException e) {
            throw new RelationAlreadyExistsException(relationType, sourceDeviceId, targetDeviceId);
        }
    }

    // ──────────────────── 启用/停用 ────────────────────

    /**
     * 启用关系。
     */
    public void enable(Long relationId) {
        DeviceRelation relation = getById(relationId);
        relation.setEnabled(true);
        relation.setUpdateTime(LocalDateTime.now());
        relationMapper.updateById(relation);
        log.info("Relation enabled: id={}", relationId);
    }

    /**
     * 停用关系。
     */
    public void disable(Long relationId) {
        DeviceRelation relation = getById(relationId);
        relation.setEnabled(false);
        relation.setUpdateTime(LocalDateTime.now());
        relationMapper.updateById(relation);
        log.info("Relation disabled: id={}", relationId);
    }

    // ──────────────────── 删除 ────────────────────

    /**
     * 物理删除关系。
     */
    public void delete(Long relationId) {
        DeviceRelation relation = getById(relationId);
        relationMapper.deleteById(relationId);
        log.info("Relation deleted: id={}", relationId);
    }

    // ──────────────────── 查询 ────────────────────

    /**
     * 查询设备的所有关系（双向）。
     * <p>
     * OUTBOUND: 设备作为 source — 查 target 侧信息
     * INBOUND:  设备作为 target — 查 source 侧信息
     *
     * @param deviceId 当前设备ID
     * @return 关系列表（含 outbound 和 inbound），每条 relation 的 targetDevice 已填充
     */
    public List<DeviceRelation> getRelations(String deviceId) {
        List<DeviceRelation> result = new ArrayList<>();

        // OUTBOUND: 当前设备是 source，关联设备是 target
        List<DeviceRelation> outbound = relationMapper.selectList(
                new LambdaQueryWrapper<DeviceRelation>()
                        .eq(DeviceRelation::getSourceDeviceId, deviceId));
        for (DeviceRelation r : outbound) {
            Device target = deviceRegistry.findByDeviceId(r.getTargetDeviceId());
            r.setTargetDevice(target);
        }
        result.addAll(outbound);

        // INBOUND: 当前设备是 target，关联设备是 source
        List<DeviceRelation> inbound = relationMapper.selectList(
                new LambdaQueryWrapper<DeviceRelation>()
                        .eq(DeviceRelation::getTargetDeviceId, deviceId));
        for (DeviceRelation r : inbound) {
            Device source = deviceRegistry.findByDeviceId(r.getSourceDeviceId());
            r.setTargetDevice(source);  // 复用 targetDevice 字段存储关联设备
        }
        result.addAll(inbound);

        return result;
    }

    /**
     * 查询设备已启用的关联设备（按类型）。
     *
     * @param sourceDeviceId 关系源设备ID
     * @param relationType   关系类型
     * @return 已启用的目标设备列表
     */
    public List<Device> getEnabledTargets(String sourceDeviceId, String relationType) {
        List<DeviceRelation> relations = relationMapper.selectList(
                new LambdaQueryWrapper<DeviceRelation>()
                        .eq(DeviceRelation::getSourceDeviceId, sourceDeviceId)
                        .eq(DeviceRelation::getRelationType, relationType)
                        .eq(DeviceRelation::getEnabled, true));

        List<Device> targets = new ArrayList<>();
        for (DeviceRelation r : relations) {
            Device target = deviceRegistry.findByDeviceId(r.getTargetDeviceId());
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    // ──────────────────── 内部方法 ────────────────────

    private DeviceRelation getById(Long relationId) {
        DeviceRelation relation = relationMapper.selectById(relationId);
        if (relation == null) {
            throw new RelationNotFoundException(relationId);
        }
        return relation;
    }
}
