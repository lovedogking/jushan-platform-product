package com.jushan.platform.modules.vehicle.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.infra.security.RequireRole;
import com.jushan.platform.modules.vehicle.dto.VehicleListCreateCmd;
import com.jushan.platform.modules.vehicle.dto.VehicleListPageQuery;
import com.jushan.platform.modules.vehicle.dto.VehicleListUpdateCmd;
import com.jushan.platform.modules.vehicle.entity.VehicleList;
import com.jushan.platform.modules.vehicle.service.VehicleListService;
import com.jushan.platform.modules.vehicle.vo.VehicleListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 车辆黑白名单管理接口。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Tag(name = "黑白名单管理")
@RestController
@RequestMapping("/api/v1/admin/vehicle-list")
@RequireRole({"platform", "tenant"}) // OP-01: 超管 + 租户管理员
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

    /**
     * Excel 批量导入白名单（OP-02）。
     * <p>
     * 使用 Apache POI 解析标准 .xlsx 文件（支持 WPS/Excel 生成），第一列为车牌号。
     * 校验：车牌格式、文件内重复、数据库已存在，返回成功/失败明细。
     */
    @Operation(summary = "Excel 批量导入白名单（OP-02）")
    @PostMapping("/import")
    @RequirePermission("vehicle-list:create")
    @BusinessLog(value = "批量导入白名单", module = "vehicle-list", operationType = "IMPORT",
            operationObject = "白名单")
    public R<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("parkingLotId") Long parkingLotId) {

        List<String> plates = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();
        int successCount = 0;
        int totalInputRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cell = row.getCell(0);
                if (cell == null) continue;

                String rawValue = getCellStringValue(cell).trim();
                if (rawValue.isEmpty()) continue;

                int rowNum = i + 1;

                // 跳过表头（如果第一行包含"车牌"字样或不是有效车牌格式）
                if (i == 0 && (rawValue.contains("车牌") || rawValue.contains("plate")
                        || rawValue.contains("Plate") || !looksLikePlate(rawValue))) {
                    continue;
                }

                totalInputRows++;
                String plate = rawValue.toUpperCase().replaceAll("[\\s,，;；]+", "");

                // 格式校验
                if (plate.isEmpty()) {
                    errors.add(Map.of("row", String.valueOf(rowNum), "plate", rawValue, "reason", "车牌号为空"));
                    continue;
                }
                if (!isValidPlateFormat(plate)) {
                    errors.add(Map.of("row", String.valueOf(rowNum), "plate", plate, "reason", "车牌格式无效"));
                    continue;
                }

                // 文件内重复校验
                if (plates.contains(plate)) {
                    errors.add(Map.of("row", String.valueOf(rowNum), "plate", plate, "reason", "文件内重复"));
                    continue;
                }
                plates.add(plate);
            }

            // 数据库重复校验 + 导入
            for (String plate : plates) {
                try {
                    if (vehicleListService.isWhitelisted(parkingLotId, plate)) {
                        errors.add(Map.of("plate", plate, "reason", "已存在于白名单中"));
                        continue;
                    }
                    VehicleListCreateCmd cmd = new VehicleListCreateCmd();
                    cmd.setPlateNumber(plate);
                    cmd.setListType(VehicleList.TYPE_WHITE);
                    cmd.setParkingLotId(parkingLotId);
                    vehicleListService.create(cmd);
                    successCount++;
                } catch (Exception e) {
                    errors.add(Map.of("plate", plate, "reason", "导入失败: " + e.getMessage()));
                }
            }

        } catch (Exception e) {
            return R.fail(500, "文件读取失败: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", totalInputRows);
        result.put("successCount", successCount);
        result.put("failCount", errors.size());
        result.put("errors", errors);
        return R.ok(result);
    }

    /** 从 POI Cell 提取字符串值，处理各种单元格类型 */
    private String getCellStringValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                // 车牌可能是纯数字，避免科学计数法
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private static final Pattern PLATE_PATTERN =
            Pattern.compile("^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁][A-HJ-NP-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳领试]$");

    private boolean isValidPlateFormat(String plate) {
        if (plate.length() < 7 || plate.length() > 8) return false;
        return PLATE_PATTERN.matcher(plate).matches();
    }

    private boolean looksLikePlate(String s) {
        return s.length() >= 7 && Character.isLetter(s.charAt(0));
    }
}
