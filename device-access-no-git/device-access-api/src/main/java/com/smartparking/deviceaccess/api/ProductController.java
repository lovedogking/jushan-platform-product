package com.smartparking.deviceaccess.api;

import com.smartparking.deviceaccess.api.dto.DeviceProductDTO;
import com.smartparking.deviceaccess.common.dto.Result;
import com.smartparking.deviceaccess.common.entity.DeviceProduct;
import com.smartparking.deviceaccess.registry.DeviceProductRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备产品目录 API。
 * <p>
 * 供前端下拉选择产品型号。
 * v0.3 新增。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final DeviceProductRegistry productRegistry;

    /**
     * 产品列表。
     */
    @GetMapping
    public Result<List<DeviceProductDTO>> listProducts() {
        log.info("API: listProducts");
        List<DeviceProduct> products = productRegistry.listAll();
        List<DeviceProductDTO> dtos = products.stream()
                .map(p -> DeviceProductDTO.builder()
                        .id(p.getId())
                        .brand(p.getBrand())
                        .model(p.getModel())
                        .productName(p.getProductName())
                        .deviceType(p.getDeviceType())
                        .protocol(p.getProtocol())
                        .capabilities(p.getCapabilityList())
                        .build())
                .collect(Collectors.toList());
        return Result.ok(dtos);
    }
}
