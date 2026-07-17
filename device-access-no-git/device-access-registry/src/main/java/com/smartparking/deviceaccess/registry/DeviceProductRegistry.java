package com.smartparking.deviceaccess.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.common.entity.DeviceProductMapper;
import com.smartparking.deviceaccess.common.enums.DeviceCapability;
import com.smartparking.deviceaccess.common.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备产品目录注册中心。
 * <p>
 * 负责 t_device_product 表的查询操作。
 * 产品目录由数据库预设数据维护，当前版本不支持运行时增删改。
 * v0.3 新增。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProductRegistry {

    private final DeviceProductMapper productMapper;

    /**
     * 查询全部产品列表。
     */
    public List<DeviceProduct> listAll() {
        return productMapper.selectList(
                new LambdaQueryWrapper<DeviceProduct>()
                        .orderByAsc(DeviceProduct::getId));
    }

    /**
     * 按 ID 查询产品，不存在则抛异常。
     */
    public DeviceProduct getById(Long id) {
        DeviceProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

    /**
     * 按设备类型查询产品列表。
     *
     * @param deviceType CAMERA / DISPLAY
     */
    public List<DeviceProduct> listByDeviceType(String deviceType) {
        return productMapper.selectList(
                new LambdaQueryWrapper<DeviceProduct>()
                        .eq(DeviceProduct::getDeviceType, deviceType));
    }

    /**
     * 按品牌+型号精确查找，不存在返回 null。
     */
    public DeviceProduct findByBrandAndModel(String brand, String model) {
        return productMapper.selectOne(
                new LambdaQueryWrapper<DeviceProduct>()
                        .eq(DeviceProduct::getBrand, brand)
                        .eq(DeviceProduct::getModel, model));
    }

    /**
     * 检查指定产品是否具备某种能力。
     * <p>
     * 能力由产品型号声明，不依赖 deviceType 推导。
     *
     * @param productId  产品 ID
     * @param capability 能力枚举
     * @return true 表示该产品支持此能力
     */
    public boolean hasCapability(Long productId, DeviceCapability capability) {
        DeviceProduct product = getById(productId);
        return product.hasCapability(capability);
    }

    /**
     * 批量按 ID 查询，返回 Map<id, DeviceProduct>。
     */
    public Map<Long, DeviceProduct> mapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DeviceProduct> products = productMapper.selectBatchIds(ids);
        return products.stream().collect(Collectors.toMap(DeviceProduct::getId, Function.identity()));
    }
}
