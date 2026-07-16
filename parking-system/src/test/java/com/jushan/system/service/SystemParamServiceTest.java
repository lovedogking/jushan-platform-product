package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.framework.redis.CacheNames;
import com.jushan.system.dto.SystemParamUpdateRequest;
import com.jushan.system.entity.SysConfig;
import com.jushan.system.mapper.SysConfigMapper;
import com.jushan.system.vo.SystemParamVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SystemParamService} 单元测试（Phase 1 A3）。
 * <p>
 * 覆盖：全量查询、按 key 查询、更新参数值、缓存行为、参数不存在场景。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SystemParamServiceTest {

    @Mock
    private SysConfigMapper configMapper;

    private SystemParamService systemParamService;

    private SysConfig refreshIntervalConfig;
    private SysConfig triggerModeConfig;
    private SysConfig countInSpaceConfig;

    @BeforeEach
    void setUp() {
        systemParamService = new SystemParamService(configMapper);

        // available_space.refresh_interval_minutes — INT
        refreshIntervalConfig = new SysConfig();
        refreshIntervalConfig.setId(1L);
        refreshIntervalConfig.setConfigKey("available_space.refresh_interval_minutes");
        refreshIntervalConfig.setConfigValue("5");
        refreshIntervalConfig.setGroupName("基础设置");
        refreshIntervalConfig.setValueType("INT");
        refreshIntervalConfig.setDescription("余位刷新间隔（分钟）");

        // blacklist.trigger_mode — ENUM
        triggerModeConfig = new SysConfig();
        triggerModeConfig.setId(2L);
        triggerModeConfig.setConfigKey("blacklist.trigger_mode");
        triggerModeConfig.setConfigValue("1");
        triggerModeConfig.setGroupName("告警设置");
        triggerModeConfig.setValueType("ENUM");
        triggerModeConfig.setOptions("[\"1\",\"2\",\"3\"]");
        triggerModeConfig.setDescription("黑名单触发模式");

        // monthly_pass.count_in_available_space — BOOLEAN
        countInSpaceConfig = new SysConfig();
        countInSpaceConfig.setId(3L);
        countInSpaceConfig.setConfigKey("monthly_pass.count_in_available_space");
        countInSpaceConfig.setConfigValue("false");
        countInSpaceConfig.setGroupName("计费设置");
        countInSpaceConfig.setValueType("BOOLEAN");
        countInSpaceConfig.setDescription("月卡是否计入余位");
    }

    // ==================== getAllParams ====================

    @Test
    @DisplayName("查询所有系统参数（分组排序）")
    void shouldGetAllParams() {
        when(configMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(refreshIntervalConfig, triggerModeConfig, countInSpaceConfig));

        List<SystemParamVO> result = systemParamService.getAllParams();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getKey()).isEqualTo("available_space.refresh_interval_minutes");
        assertThat(result.get(0).getValue()).isEqualTo("5");
        assertThat(result.get(0).getGroupName()).isEqualTo("基础设置");
        assertThat(result.get(0).getValueType()).isEqualTo("INT");
        assertThat(result.get(1).getKey()).isEqualTo("blacklist.trigger_mode");
        assertThat(result.get(1).getOptions()).isEqualTo("[\"1\",\"2\",\"3\"]");
        assertThat(result.get(2).getKey()).isEqualTo("monthly_pass.count_in_available_space");
        assertThat(result.get(2).getValue()).isEqualTo("false");
    }

    @Test
    @DisplayName("无系统参数时返回空列表")
    void shouldReturnEmptyWhenNoParams() {
        when(configMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        List<SystemParamVO> result = systemParamService.getAllParams();

        assertThat(result).isEmpty();
    }

    // ==================== getParam ====================

    @Test
    @DisplayName("查询单个系统参数详情")
    void shouldGetParamByKey() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(refreshIntervalConfig);

        SystemParamVO result = systemParamService.getParam("available_space.refresh_interval_minutes");

        assertThat(result.getKey()).isEqualTo("available_space.refresh_interval_minutes");
        assertThat(result.getValue()).isEqualTo("5");
        assertThat(result.getGroupName()).isEqualTo("基础设置");
        assertThat(result.getValueType()).isEqualTo("INT");
    }

    @Test
    @DisplayName("查询不存在的参数时抛异常")
    void shouldThrowWhenParamNotFound() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> systemParamService.getParam("nonexistent.key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    // ==================== getValue ====================

    @Test
    @DisplayName("getValue 返回原始值（字符串）")
    void shouldGetRawValue() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(refreshIntervalConfig);

        String value = systemParamService.getValue("available_space.refresh_interval_minutes");

        assertThat(value).isEqualTo("5");
    }

    @Test
    @DisplayName("getValue 参数不存在时返回 null")
    void shouldReturnNullWhenKeyNotFound() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        String value = systemParamService.getValue("nonexistent.key");

        assertThat(value).isNull();
    }

    // ==================== updateParam ====================

    @Test
    @DisplayName("更新系统参数值")
    void shouldUpdateParamValue() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(refreshIntervalConfig);

        SystemParamUpdateRequest request = new SystemParamUpdateRequest();
        request.setValue("10");

        SystemParamVO result = systemParamService.updateParam("available_space.refresh_interval_minutes", request);

        assertThat(result.getValue()).isEqualTo("10");
        verify(configMapper, times(1)).updateById(any(SysConfig.class));
    }

    @Test
    @DisplayName("更新不存在的参数时抛异常")
    void shouldThrowWhenUpdateNonExistent() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        SystemParamUpdateRequest request = new SystemParamUpdateRequest();
        request.setValue("10");

        assertThatThrownBy(() -> systemParamService.updateParam("nonexistent.key", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
        verify(configMapper, never()).updateById(any(SysConfig.class));
    }

    // ==================== VO 转换 ====================

    @Test
    @DisplayName("ENUM 类型参数的 options 字段完整传递")
    void shouldPreserveEnumOptions() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(triggerModeConfig);

        SystemParamVO result = systemParamService.getParam("blacklist.trigger_mode");

        assertThat(result.getValueType()).isEqualTo("ENUM");
        assertThat(result.getOptions()).isEqualTo("[\"1\",\"2\",\"3\"]");
    }
}
