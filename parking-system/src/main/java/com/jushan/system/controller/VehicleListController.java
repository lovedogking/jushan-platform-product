package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.VehicleListCreateCmd;
import com.jushan.system.dto.VehicleListPageQuery;
import com.jushan.system.dto.VehicleListUpdateCmd;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.service.VehicleListService;
import com.jushan.system.vo.VehicleListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 车辆黑白名单管理接口。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Tag(name = "黑白名单管理")
@RestController
@RequestMapping("/api/v1/admin/vehicle-list")
public class VehicleListController {

    private final VehicleListService vehicleListService;

    public VehicleListController(VehicleListService vehicleListService) {
        this.vehicleListService = vehicleListService;
    }

    @Operation(summary = "创建名单")
    @PostMapping
    @RequirePermission("vehicle-list:create")
    @BusinessLog(value = "创建黑白名单", module = "vehicle-list", operationType = "CREATE",
            operationObject = "黑白名单", objectIdExpression = "#result?.data?.id")
    public R<VehicleListVO> create(@RequestBody @Valid VehicleListCreateCmd cmd) {
        VehicleList entity = vehicleListService.create(cmd);
        return R.ok(vehicleListService.detail(entity.getId()));
    }

    @Operation(summary = "编辑名单")
    @PutMapping("/{id}")
    @RequirePermission("vehicle-list:update")
    @BusinessLog(value = "编辑黑白名单", module = "vehicle-list", operationType = "UPDATE",
            operationObject = "黑白名单", objectIdExpression = "#id")
    public R<VehicleListVO> update(@PathVariable Long id, @RequestBody @Valid VehicleListUpdateCmd cmd) {
        VehicleList entity = vehicleListService.update(id, cmd);
        return R.ok(vehicleListService.detail(entity.getId()));
    }

    @Operation(summary = "删除名单")
    @DeleteMapping("/{id}")
    @RequirePermission("vehicle-list:delete")
    @BusinessLog(value = "删除黑白名单", module = "vehicle-list", operationType = "DELETE",
            operationObject = "黑白名单", objectIdExpression = "#id")
    public R<Void> delete(@PathVariable Long id) {
        vehicleListService.delete(id);
        return R.ok();
    }

    @Operation(summary = "分页查询名单")
    @GetMapping("/page")
    @RequirePermission("vehicle-list:view")
    public R<IPage<VehicleListVO>> page(@Valid VehicleListPageQuery query) {
        return R.ok(vehicleListService.pageList(query));
    }

    @Operation(summary = "名单详情")
    @GetMapping("/{id}")
    @RequirePermission("vehicle-list:view")
    public R<VehicleListVO> detail(@PathVariable Long id) {
        return R.ok(vehicleListService.detail(id));
    }

    @Operation(summary = "黑名单触发类型字典")
    @GetMapping("/trigger-types")
    @RequirePermission("vehicle-list:view")
    public R<List<Map<String, String>>> triggerTypes() {
        List<Map<String, String>> types = Arrays.asList(
                Map.of("code", VehicleList.TRIGGER_ARREARS, "label", "欠费类"),
                Map.of("code", VehicleList.TRIGGER_MANAGEMENT, "label", "管理类"),
                Map.of("code", VehicleList.TRIGGER_OTHER, "label", "其他类")
        );
        return R.ok(types);
    }
}
