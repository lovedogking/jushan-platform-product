package com.jushan.boot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.TenantContext;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.DeviceCommandAudit;
import com.jushan.system.entity.DeviceModel;
import com.jushan.system.entity.DeviceVendor;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.SysUser;
import com.jushan.system.entity.Tenant;
import com.jushan.system.mapper.DeviceCommandAuditMapper;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DeviceModelMapper;
import com.jushan.system.mapper.DeviceVendorMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.SysUserMapper;
import com.jushan.system.mapper.TenantMapper;
import com.jushan.system.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 开闸占位与命令审计集成测试（P001）。
 * <p>
 * 验证 DeviceAccessClient.openGate 占位行为、以及 DeviceService.openGatePlaceholder
 * 在 v0.2 阶段正确记录 {@code device_command_audit} 审计记录。
 * 真实开闸调用需在 Device Access v1.0 契约冻结后实现。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@DisplayName("开闸占位与命令审计集成测试")
class OpenGatePlaceholderIntegrationTest extends TestcontainersBaseTest {

    @Autowired private DeviceService deviceService;
    @Autowired private DeviceCommandAuditMapper commandAuditMapper;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private DeviceVendorMapper vendorMapper;
    @Autowired private DeviceModelMapper modelMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;
    @Autowired private TenantMapper tenantMapper;
    @Autowired private SysUserMapper sysUserMapper;

    private Long tenantId;
    private Long parkingLotId;
    private Long gateDeviceId;
    private Long cameraDeviceId;
    private static final String GATE_SN = "GATE_SN_P001";
    private static final String CAMERA_SN = "CAMERA_SN_P001";

    @BeforeEach
    void setUp() {
        cleanupTestData();

        // 创建测试租户
        Tenant tenant = new Tenant();
        tenant.setName("P001测试租户");
        tenant.setContactPerson("测试");
        tenant.setContactPhone("13900000001");
        tenant.setStatus("ENABLED");
        tenant.setMaxParkingLots(10);
        tenant.setMaxDevices(100);
        tenant.setMaxEmployees(100);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);
        tenantId = tenant.getId();

        // 创建平台用户（用于全量访问）
        SysUser platformUser = new SysUser();
        platformUser.setUsername("p001_platform");
        platformUser.setPasswordHash("$2a$10$dummy");
        platformUser.setDisplayName("P001平台用户");
        platformUser.setStatus("ENABLED");
        platformUser.setRoles("[\"super_admin\"]");
        platformUser.setCredentialStatus("ACTIVE");
        platformUser.setCreatedAt(LocalDateTime.now());
        platformUser.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(platformUser);

        // 创建停车场
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(tenantId);
        lot.setName("P001测试停车场");
        lot.setTotalSpaces(100);
        lot.setStatus("ENABLED");
        lot.setPaymentMode("PLATFORM");
        lot.setImageRetentionDays(30);
        lot.setDataRetentionDays(365);
        lot.setFreeExitMinutes(15);
        lot.setManualReleasePolicy("ADMIN_ONLY");
        lot.setOfflinePolicy("ALLOW_ENTRY_EXIT");
        lot.setCreatedAt(LocalDateTime.now());
        lot.setUpdatedAt(LocalDateTime.now());
        parkingLotMapper.insert(lot);
        parkingLotId = lot.getId();

        // 使用已有厂商 ZHENSHI（T20 迁移已初始化）
        DeviceVendor vendor = vendorMapper.selectOne(
                new LambdaQueryWrapper<DeviceVendor>().eq(DeviceVendor::getCode, "ZHENSHI"));
        assertThat(vendor).isNotNull();

        // 使用已有型号
        DeviceModel model = modelMapper.selectOne(
                new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getVendorId, vendor.getId()));
        assertThat(model).isNotNull();

        // 创建 CAMERA 设备
        Device camera = new Device();
        camera.setParkingLotId(parkingLotId);
        camera.setVendorId(vendor.getId());
        camera.setModelId(model.getId());
        camera.setName("P001入口相机");
        camera.setCode("P001_CAM");
        camera.setDeviceSn(CAMERA_SN);
        camera.setDeviceType("CAMERA");
        camera.setStatus("ENABLED");
        camera.setCapabilities("RECOGNIZE,CAPTURE");
        camera.setCreatedAt(LocalDateTime.now());
        camera.setUpdatedAt(LocalDateTime.now());
        deviceMapper.insert(camera);
        cameraDeviceId = camera.getId();

        // 创建 GATE 设备
        Device gate = new Device();
        gate.setParkingLotId(parkingLotId);
        gate.setVendorId(vendor.getId());
        gate.setModelId(model.getId());
        gate.setName("P001出口道闸");
        gate.setCode("P001_GATE");
        gate.setDeviceSn(GATE_SN);
        gate.setDeviceType("GATE");
        gate.setStatus("ENABLED");
        gate.setCapabilities("GATE_OPEN");
        gate.setCreatedAt(LocalDateTime.now());
        gate.setUpdatedAt(LocalDateTime.now());
        deviceMapper.insert(gate);
        gateDeviceId = gate.getId();

        // 设置平台用户上下文
        TenantContext.set(new TenantContext.Snapshot(
                null, platformUser.getId(), "platform", "[\"super_admin\"]"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        cleanupTestData();
    }

    @Test
    @DisplayName("openGatePlaceholder → 抛 UNSUPPORTED_OPERATION 并记录 NOT_IMPLEMENTED 审计")
    void shouldRecordNotImplementedAuditAndThrowUnsupported() {
        assertThatThrownBy(() ->
                deviceService.openGatePlaceholder(gateDeviceId, "P001占位测试", "MANUAL", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(CommonErrorCode.UNSUPPORTED_OPERATION.getCode());
                    assertThat(be.getMessage()).contains("NOT_IMPLEMENTED_IN_V0.2");
                });

        List<DeviceCommandAudit> audits = commandAuditMapper.selectList(
                new LambdaQueryWrapper<DeviceCommandAudit>()
                        .eq(DeviceCommandAudit::getDeviceId, gateDeviceId)
                        .eq(DeviceCommandAudit::getCommandType, DeviceService.COMMAND_TYPE_OPEN_GATE));

        assertThat(audits).hasSize(1);
        DeviceCommandAudit audit = audits.get(0);
        assertThat(audit.getStatus()).isEqualTo(DeviceService.AUDIT_STATUS_NOT_IMPLEMENTED);
        assertThat(audit.getUncertain()).isFalse();
        assertThat(audit.getDeviceSn()).isEqualTo(GATE_SN);
        assertThat(audit.getParkingLotId()).isEqualTo(parkingLotId);
        assertThat(audit.getSource()).isEqualTo("MANUAL");
        assertThat(audit.getReason()).isEqualTo("P001占位测试");
        assertThat(audit.getErrorCode()).isEqualTo(String.valueOf(CommonErrorCode.UNSUPPORTED_OPERATION.getCode()));
        assertThat(audit.getErrorMessage()).contains("NOT_IMPLEMENTED_IN_V0.2");
        assertThat(audit.getRequestPayload()).contains("\"commandType\":\"OPEN_GATE\"");
        assertThat(audit.getIssuedAt()).isNotNull();
        assertThat(audit.getCompletedAt()).isNotNull();
        assertThat(audit.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("CAMERA 设备调用 openGatePlaceholder → 拒绝")
    void shouldRejectCameraDeviceForOpenGate() {
        assertThatThrownBy(() ->
                deviceService.openGatePlaceholder(cameraDeviceId, "相机不能开闸", "MANUAL", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅逻辑道闸");

        List<DeviceCommandAudit> audits = commandAuditMapper.selectList(
                new LambdaQueryWrapper<DeviceCommandAudit>()
                        .eq(DeviceCommandAudit::getDeviceId, cameraDeviceId));
        assertThat(audits).isEmpty();
    }

    @Test
    @DisplayName("已停用 GATE 设备调用 openGatePlaceholder → 拒绝")
    void shouldRejectDisabledGateDevice() {
        Device gate = deviceMapper.selectById(gateDeviceId);
        gate.setStatus("DISABLED");
        gate.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(gate);

        assertThatThrownBy(() ->
                deviceService.openGatePlaceholder(gateDeviceId, "停用设备不能开闸", "MANUAL", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");

        List<DeviceCommandAudit> audits = commandAuditMapper.selectList(
                new LambdaQueryWrapper<DeviceCommandAudit>()
                        .eq(DeviceCommandAudit::getDeviceId, gateDeviceId));
        assertThat(audits).isEmpty();
    }

    @Test
    @DisplayName("带 previousCommandId 调用 → 审计记录保存关联关系")
    void shouldRecordPreviousCommandId() {
        String previousCommandId = "cmd_prev_01JZ8S2V3K7FX2W8";

        assertThatThrownBy(() ->
                deviceService.openGatePlaceholder(gateDeviceId, "人工再次开闸", "MANUAL", previousCommandId))
                .isInstanceOf(BusinessException.class);

        List<DeviceCommandAudit> audits = commandAuditMapper.selectList(
                new LambdaQueryWrapper<DeviceCommandAudit>()
                        .eq(DeviceCommandAudit::getDeviceId, gateDeviceId));
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getPreviousCommandId()).isEqualTo(previousCommandId);
    }

    private void cleanupTestData() {
        commandAuditMapper.delete(new LambdaQueryWrapper<>());
        deviceMapper.delete(new LambdaQueryWrapper<Device>()
                .like(Device::getDeviceSn, "P001_")
                .or()
                .like(Device::getCode, "P001_"));
        parkingLotMapper.delete(new LambdaQueryWrapper<ParkingLot>()
                .eq(ParkingLot::getName, "P001测试停车场"));
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "p001_platform"));
        tenantMapper.delete(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getContactPhone, "13900000001"));
    }
}
