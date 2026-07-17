package com.smartparking.deviceaccess.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.smartparking.deviceaccess.common.enums.DeviceCapability;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备产品目录实体。
 * <p>
 * 记录可接入的设备产品型号（品牌+型号+类型+协议）。
 * device 通过 product_id 关联到此表，实现产品信息与设备实例的解耦。
 * v0.3 新增。
 */
@Data
@TableName("t_device_product")
public class DeviceProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 品牌，如"臻识"、"信路通" */
    private String brand;

    /** 型号，如"C5H" */
    private String model;

    /** 产品全称，如"臻识 C5H" */
    private String productName;

    /** 设备类型：CAMERA / DISPLAY */
    private String deviceType;

    /** 通信协议：MQTT / RS485 / NONE */
    private String protocol;

    /**
     * 设备能力列表（JSON 数组格式）。
     * 由产品型号决定，如 {@code ["DISPLAY_TEXT","DISPLAY_SAVE","DISPLAY_CONTROL","TIME_SYNC"]}。
     * 空数组表示无特殊控制能力。
     */
    private String capabilities;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 解析 capabilities JSON 数组为枚举列表。
     * <p>
     * 与 {@link #hasCapability(DeviceCapability)} 共用同一套解析逻辑，
     * 确保前后端判断一致。
     *
     * @return 能力枚举列表；解析失败或为空时返回空列表
     */
    public java.util.List<DeviceCapability> getCapabilityList() {
        java.util.List<DeviceCapability> list = new java.util.ArrayList<>();
        if (capabilities == null || capabilities.isBlank()) {
            return list;
        }
        String content = capabilities.trim();
        if (!content.startsWith("[") || !content.endsWith("]")) {
            return list;
        }
        String inner = content.substring(1, content.length() - 1).trim();
        if (inner.isEmpty()) {
            return list;
        }
        String[] items = inner.split(",");
        for (String item : items) {
            String name = item.trim();
            if (name.startsWith("\"") && name.endsWith("\"")) {
                name = name.substring(1, name.length() - 1);
            }
            try {
                list.add(DeviceCapability.valueOf(name));
            } catch (IllegalArgumentException e) {
                // 忽略非法值，与 hasCapability 行为一致
            }
        }
        return list;
    }

    /**
     * 检查产品是否具备指定能力。
     * <p>
     * 解析 JSON 数组格式的 capabilities 字段，按枚举名称精确匹配。
     * 不依赖第三方 JSON 库，仅处理 {@code ["A","B"]} 格式。
     *
     * @param capability 能力枚举值
     * @return true 表示该产品支持此能力
     */
    public boolean hasCapability(DeviceCapability capability) {
        return getCapabilityList().contains(capability);
    }
}
