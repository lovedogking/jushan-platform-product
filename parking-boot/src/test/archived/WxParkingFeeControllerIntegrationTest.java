package com.jushan.boot.controller;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.BillingRule;
import com.jushan.system.entity.BillingRuleVersion;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.mapper.BillingRuleMapper;
import com.jushan.system.mapper.BillingRuleVersionMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序停车费用查询集成测试（P007）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "wx.mock-login=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "wx.mock-login=true"
})
@DisplayName("小程序停车费用查询集成测试")
class WxParkingFeeControllerIntegrationTest extends TestcontainersBaseTest {

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParkingLotMapper parkingLotMapper;
    @Autowired
    private BillingRuleMapper billingRuleMapper;
    @Autowired
    private BillingRuleVersionMapper billingRuleVersionMapper;
    @Autowired
    private ParkingRecordMapper parkingRecordMapper;
    @Autowired
    private ParkingOrderMapper parkingOrderMapper;

    private String wxToken;
    private Long parkingLotId;
    private Long recordId;
    private static final String PLATE = "京A12345";

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();

        String mockCode = "fee_wx_test_" + System.currentTimeMillis();
        String loginResp = mockMvc.perform(post("/wx/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mockCode + "\",\"nickname\":\"费用测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        wxToken = extractToken(loginResp);

        mockMvc.perform(post("/wx/plates")
                        .header("Authorization", "Bearer " + wxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + PLATE + "\",\"vehicleType\":\"SMALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        parkingLotId = createParkingLotAndRule(30, 500);
        recordId = createParkingRecord(parkingLotId, PLATE, ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(90));
    }

    @Test
    @DisplayName("按车牌查询当前停车费用成功")
    void shouldQueryFeeByPlate() throws Exception {
        mockMvc.perform(get("/wx/parking/fee?plate=" + PLATE)
                        .header("Authorization", "Bearer " + wxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.plate").value(PLATE))
                .andExpect(jsonPath("$.data.feeCents").value(500))
                .andExpect(jsonPath("$.data.payable").value(true))
                .andExpect(jsonPath("$.data.freeExitDeadline").exists());
    }

    @Test
    @DisplayName("按记录 ID 查询当前停车费用成功")
    void shouldQueryFeeByRecordId() throws Exception {
        mockMvc.perform(get("/wx/parking/fee/" + recordId)
                        .header("Authorization", "Bearer " + wxToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.feeCents").value(500));
    }

    @Test
    @DisplayName("结算预览费用成功")
    void shouldPreviewFee() throws Exception {
        mockMvc.perform(post("/wx/parking/fee/preview")
                        .header("Authorization", "Bearer " + wxToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordId\":" + recordId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.previewExitTime").exists())
                .andExpect(jsonPath("$.data.feeCents").value(500));
    }

    @Test
    @DisplayName("查询未绑定车牌 -> 403")
    void shouldRejectUnboundPlate() throws Exception {
        mockMvc.perform(get("/wx/parking/fee?plate=京B99999")
                        .header("Authorization", "Bearer " + wxToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登录 -> 401")
    void shouldRejectWithoutLogin() throws Exception {
        mockMvc.perform(get("/wx/parking/fee?plate=" + PLATE))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 辅助方法 ====================

    private Long createParkingLotAndRule(int freeMinutes, int firstAmount) {
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(1L);
        lot.setName("小程序费用测试停车场");
        lot.setTotalSpaces(100);
        lot.setCurrentVehicles(0);
        lot.setRemainingSpaces(100);
        lot.setStatus("ENABLED");
        lot.setAddress("测试地址");
        parkingLotMapper.insert(lot);

        BillingRule rule = new BillingRule();
        rule.setTenantId(1L);
        rule.setParkingLotId(lot.getId());
        rule.setName("测试规则");
        rule.setRuleType(BillingRule.RULE_TYPE_FIXED);
        rule.setStatus(BillingRule.STATUS_ENABLED);
        billingRuleMapper.insert(rule);

        BillingRuleVersion version = new BillingRuleVersion();
        version.setRuleId(rule.getId());
        version.setTenantId(1L);
        version.setParkingLotId(lot.getId());
        version.setVersion(1);
        version.setIsActive(1);
        version.setFreeMinutes(freeMinutes);
        version.setFirstAmount(firstAmount);
        billingRuleVersionMapper.insert(version);

        return lot.getId();
    }

    private Long createParkingRecord(Long parkingLotId, String plate, String status,
                                      LocalDateTime entryTime) {
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(1L);
        record.setParkingLotId(parkingLotId);
        record.setStandardizedPlate(plate);
        record.setStatus(status);
        record.setEntryTime(entryTime);
        parkingRecordMapper.insert(record);
        return record.getId();
    }

    private void cleanupTestData() {
        parkingRecordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingOrderMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        billingRuleVersionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        billingRuleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingLotMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"token\":\"") + 9;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
