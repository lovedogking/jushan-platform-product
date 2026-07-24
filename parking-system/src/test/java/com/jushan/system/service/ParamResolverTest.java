package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.system.cache.ParamCacheStore;
import com.jushan.system.constant.ParamKeys;
^import com.jushan.platform.modules.common.entity.SysConfig;
import com.jushan.system.mapper.SysConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ParamResolver} 单元测试（任务包 1-1）。
 * <p>
 * 覆盖验收标准：
 * <ol>
 *   <li>优先级解析三条路径：车场覆盖 / 回退全局 / 回退默认值。</li>
 *   <li>缓存命中不重复查库；修改车场参数后缓存立即失效、读取方拿到新值。</li>
 *   <li>全局参数失效（{@link ParamResolver#evictGlobal(String)}）后回退车场立即拿到新全局值。</li>
 *   <li>值类型/枚举校验；非法车场/键拒绝。</li>
 * </ol>
 * <p>
 * 使用真实的进程内 {@link ParamCacheStore}（{@link MapParamCacheStore}）验证缓存与失效行为，
 * 数据库以 Mockito 桩模拟。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
class ParamResolverTest {

    private static final long LOT_ID = 1001L;

    @Mock
    private SysConfigMapper configMapper;

    private MapParamCacheStore cache;
    private ParamResolver paramResolver;

    @BeforeEach
    void setUp() {
        cache = new MapParamCacheStore();
        paramResolver = new ParamResolver(configMapper, cache);
    }

    // ==================== 验收 1：优先级解析三路径 ====================

    @Test
    @DisplayName("路径一：命中车场级覆盖值")
    void shouldResolveLotOverride() {
        // 车场层查询返回覆盖行
        when(configMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(row(ParamKeys.EXIT_UNPAID_STRATEGY, "ALLOW_ARREARS"));

        String value = paramResolver.getString(ParamKeys.EXIT_UNPAID_STRATEGY, LOT_ID);

        assertThat(value).isEqualTo("ALLOW_ARREARS");
        // 命中车场级后不再查询全局层：仅一次查库
        verify(configMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("路径二：车场级缺省，回退全局值")
    void shouldFallbackToGlobal() {
        // 第 1 次（车场层）返回 null，第 2 次（全局层）返回全局行
        when(configMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null, row(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, "10"));

        String value = paramResolver.getString(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, LOT_ID);

        assertThat(value).isEqualTo("10");
        verify(configMapper, times(2)).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("路径三：车场级与全局均缺省，回退代码默认值")
    void shouldFallbackToDefault() {
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        String value = paramResolver.getString(ParamKeys.PAY_EXIT_WINDOW_MINUTES, LOT_ID);

        // 目录默认值为 15
        assertThat(value).isEqualTo("15");
    }

    @Test
    @DisplayName("getInt / getBoolean 解析")
    void shouldParseTypedValues() {
        when(configMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(row(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, "30"));
        assertThat(paramResolver.getInt(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, LOT_ID, 15)).isEqualTo(30);

        // 全局/车场均无 → 用兜底
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        assertThat(paramResolver.getBoolean(ParamKeys.MONTHLY_PASS_COUNT_IN_AVAILABLE_SPACE, 2002L, false)).isFalse();
    }

    // ==================== 验收 2：缓存命中与失效 ====================

    @Test
    @DisplayName("缓存命中：同键第二次读取不再查库")
    void shouldServeFromCacheOnSecondRead() {
        when(configMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(row(ParamKeys.EXIT_UNPAID_STRATEGY, "BLOCK"));

        String first = paramResolver.getString(ParamKeys.EXIT_UNPAID_STRATEGY, LOT_ID);
        String second = paramResolver.getString(ParamKeys.EXIT_UNPAID_STRATEGY, LOT_ID);

        assertThat(first).isEqualTo("BLOCK");
        assertThat(second).isEqualTo("BLOCK");
        // 第二次命中缓存：全程仅一次查库
        verify(configMapper, times(1)).selectOne(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("修改车场参数后缓存立即失效，读取方拿到新值")
    void shouldInvalidateCacheAfterLotParamUpdate() {
        // read1（车场层）="15"；setLotParam existing 查询="15"；read2（车场层）="30"
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(
                row(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, "15"),
                row(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, "15"),
                row(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, "30"));

        // 初次读取并缓存
        assertThat(paramResolver.getString(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, LOT_ID)).isEqualTo("15");
        assertThat(cache.map).containsKey("lot:" + LOT_ID + ":" + ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES);

        // 修改车场参数 → 应失效该车场层缓存
        paramResolver.setLotParam(LOT_ID, ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, "30");
        assertThat(cache.map).doesNotContainKey("lot:" + LOT_ID + ":" + ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES);

        // 再次读取 → 缓存未命中，回源拿到新值
        assertThat(paramResolver.getString(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, LOT_ID)).isEqualTo("30");
    }

    @Test
    @DisplayName("全局参数失效后，回退全局的车场立即拿到新全局值")
    void shouldReflectGlobalChangeAfterEvictGlobal() {
        // 车场层始终为 null（未覆盖），首读后被缓存为"缺省"，evictGlobal 仅失效全局层；
        // 故第二次读取只会重查全局层：全局层先 "7" 后 "3"
        when(configMapper.selectOne(any(QueryWrapper.class))).thenReturn(
                null, row(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, "7"),
                row(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, "3"));

        assertThat(paramResolver.getString(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, LOT_ID)).isEqualTo("7");

        // 模拟全局参数变更后失效全局缓存层
        paramResolver.evictGlobal(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS);
        assertThat(cache.map).doesNotContainKey("global:" + ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS);

        assertThat(paramResolver.getString(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, LOT_ID)).isEqualTo("3");
    }

    // ==================== 验收 4：写入校验 ====================

    @Test
    @DisplayName("重置车场参数删除覆盖行并失效缓存")
    void shouldResetLotParam() {
        // 预置车场层缓存
        cache.put("lot:" + LOT_ID + ":" + ParamKeys.EXIT_UNPAID_STRATEGY, "ALLOW_ARREARS");

        paramResolver.resetLotParam(LOT_ID, ParamKeys.EXIT_UNPAID_STRATEGY);

        verify(configMapper, times(1)).delete(any(QueryWrapper.class));
        assertThat(cache.map).doesNotContainKey("lot:" + LOT_ID + ":" + ParamKeys.EXIT_UNPAID_STRATEGY);
    }

    @Test
    @DisplayName("非法枚举值被拒绝")
    void shouldRejectInvalidEnum() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        paramResolver.setLotParam(LOT_ID, ParamKeys.EXIT_UNPAID_STRATEGY, "NOT_A_VALUE"))
                .isInstanceOf(com.jushan.common.BusinessException.class);
    }

    @Test
    @DisplayName("非整数值被拒绝")
    void shouldRejectNonInteger() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        paramResolver.setLotParam(LOT_ID, ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, "abc"))
                .isInstanceOf(com.jushan.common.BusinessException.class);
    }

    @Test
    @DisplayName("非车场级参数键被拒绝")
    void shouldRejectUnknownKey() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        paramResolver.setLotParam(LOT_ID, "not.a.lot.param", "1"))
                .isInstanceOf(com.jushan.common.BusinessException.class);
    }

    @Test
    @DisplayName("非法车场ID被拒绝")
    void shouldRejectInvalidLotId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        paramResolver.setLotParam(0L, ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, "15"))
                .isInstanceOf(com.jushan.common.BusinessException.class);
    }

    // ==================== 辅助 ====================

    private SysConfig row(String key, String value) {
        SysConfig c = new SysConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        return c;
    }

    /** 进程内缓存实现，供测试直接断言缓存内容与失效。 */
    private static class MapParamCacheStore implements ParamCacheStore {
        final Map<String, String> map = new HashMap<>();

        @Override
        public String get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void evict(String key) {
            map.remove(key);
        }
    }
}
