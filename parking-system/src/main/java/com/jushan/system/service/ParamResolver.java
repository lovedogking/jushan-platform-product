package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.cache.ParamCacheStore;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.entity.SysConfig;
import com.jushan.system.mapper.SysConfigMapper;
import com.jushan.system.vo.ParkingLotParamVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 车场级参数解析服务（任务包 1-1）。
 * <p>
 * 提供两层参数体系的统一读取入口，解析优先级：<b>车场级 → 全局 → 代码默认值</b>
 * （默认值取自 {@link ParamKeys#LOT_PARAMS} 目录）。
 * <p>
 * <strong>缓存</strong>：分层缓存（车场层 / 全局层各自独立），遵循项目现有 Redis 缓存方案
 * （{@link ParamCacheStore} → {@code StringRedisTemplate}，无 Redis 时进程内降级）。
 * 分层设计使"全局参数变更"只需失效全局层键即可对所有回退到全局的车场立即生效，
 * 无需通配删除车场层；"车场参数变更"只失效对应车场层键。变更后立即失效，读取方立即拿到新值。
 * <p>
 * <strong>职责边界</strong>：本服务只做数据读写与缓存管理，<b>不做权限校验</b>；
 * 写操作的角色/车场范围鉴权由调用方（Controller）通过 {@code ParkingLotScopeResolver} 完成。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Service
public class ParamResolver {

    private static final Logger log = LoggerFactory.getLogger(ParamResolver.class);

    /** 缓存"该键无值"的哨兵，防止缓存穿透。用户配置值不可能等于此串。 */
    private static final String NULL_SENTINEL = "\u0000__ABSENT__";

    /** 解析来源：命中车场级覆盖值。 */
    public static final String SOURCE_LOT = "LOT";
    /** 解析来源：回退到全局值。 */
    public static final String SOURCE_GLOBAL = "GLOBAL";
    /** 解析来源：回退到代码默认值。 */
    public static final String SOURCE_DEFAULT = "DEFAULT";

    private final SysConfigMapper configMapper;
    private final ParamCacheStore cache;

    public ParamResolver(SysConfigMapper configMapper, ParamCacheStore cache) {
        this.configMapper = configMapper;
        this.cache = cache;
    }

    // ==================== 读取 ====================

    /**
     * 按"车场级 → 全局 → 默认值"解析参数字符串值。
     *
     * @param key   参数键（建议引用 {@link ParamKeys} 常量）
     * @param lotId 车场 ID；{@code null} 或 0 表示只解析全局/默认
     * @return 解析后的字符串值；均缺省时返回代码默认值（非目录参数返回 {@code null}）
     */
    public String getString(String key, Long lotId) {
        if (key == null) {
            return null;
        }
        // 1. 车场级
        if (lotId != null && lotId > ParamKeys.GLOBAL_LOT_ID) {
            String lotValue = readLayer(lotKey(lotId, key), lotId, key);
            if (lotValue != null) {
                return lotValue;
            }
        }
        // 2. 全局
        String globalValue = readLayer(globalKey(key), ParamKeys.GLOBAL_LOT_ID, key);
        if (globalValue != null) {
            return globalValue;
        }
        // 3. 代码默认值
        return ParamKeys.defaultValue(key);
    }

    /**
     * 解析整型参数。
     *
     * @param key      参数键
     * @param lotId    车场 ID
     * @param fallback 解析失败或缺省时的兜底值
     * @return 整型值
     */
    public int getInt(String key, Long lotId, int fallback) {
        String value = getString(key, lotId);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("参数非法整型，使用兜底值: key={} lotId={} value={} fallback={}", key, lotId, value, fallback);
            return fallback;
        }
    }

    /**
     * 解析布尔参数（"true" 忽略大小写为真，其余为假）。
     *
     * @param key      参数键
     * @param lotId    车场 ID
     * @param fallback 缺省时的兜底值
     * @return 布尔值
     */
    public boolean getBoolean(String key, Long lotId, boolean fallback) {
        String value = getString(key, lotId);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    // ==================== 运营端：列举车场参数（含继承信息） ====================

    /**
     * 列举某车场的 7 项车场级参数，含"生效值 / 是否覆盖 / 继承值 / 来源"。
     * <p>
     * 供车场设置页"车场参数"配置区渲染：未覆盖时前端展示"继承"标识与继承值。
     *
     * @param lotId 车场 ID
     * @return 参数视图列表（顺序与 {@link ParamKeys#LOT_PARAMS} 一致）
     */
    public List<ParkingLotParamVO> listLotParams(Long lotId) {
        List<ParkingLotParamVO> result = new ArrayList<>(ParamKeys.LOT_PARAMS.size());
        for (ParamKeys.Definition def : ParamKeys.LOT_PARAMS) {
            String key = def.key();
            String lotValue = (lotId != null && lotId > ParamKeys.GLOBAL_LOT_ID)
                    ? readLayer(lotKey(lotId, key), lotId, key) : null;
            String globalValue = readLayer(globalKey(key), ParamKeys.GLOBAL_LOT_ID, key);
            String inheritedValue = (globalValue != null) ? globalValue : def.defaultValue();

            boolean overridden = lotValue != null;
            String effective = overridden ? lotValue : inheritedValue;
            String source = overridden
                    ? SOURCE_LOT
                    : (globalValue != null ? SOURCE_GLOBAL : SOURCE_DEFAULT);

            ParkingLotParamVO vo = new ParkingLotParamVO();
            vo.setKey(key);
            vo.setDescription(def.description());
            vo.setGroupName(def.groupName());
            vo.setValueType(def.valueType());
            vo.setOptions(def.options());
            vo.setValue(effective);
            vo.setInheritedValue(inheritedValue);
            vo.setOverridden(overridden);
            vo.setSource(source);
            result.add(vo);
        }
        return result;
    }

    // ==================== 写入（数据 + 缓存，无鉴权） ====================

    /**
     * 设置（新增或更新）车场级参数覆盖值。
     * <p>
     * <strong>不做权限校验</strong>，调用方须先完成角色/车场范围鉴权。
     *
     * @param lotId 车场 ID（必须为有效车场，非全局哨兵）
     * @param key   参数键（必须是 7 项车场级参数之一）
     * @param value 新值（按值类型/枚举校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setLotParam(Long lotId, String key, String value) {
        if (lotId == null || lotId <= ParamKeys.GLOBAL_LOT_ID) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车场 ID 非法");
        }
        ParamKeys.Definition def = ParamKeys.definition(key);
        if (def == null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "不支持的车场级参数: " + key);
        }
        String normalized = validateAndNormalize(def, value);

        SysConfig existing = configMapper.selectOne(new QueryWrapper<SysConfig>()
                .eq("config_key", key)
                .eq("parking_lot_id", lotId));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setConfigValue(normalized);
            existing.setParamLevel(ParamKeys.LEVEL_LOT);
            existing.setUpdatedAt(now);
            configMapper.updateById(existing);
        } else {
            SysConfig row = new SysConfig();
            row.setConfigKey(key);
            row.setConfigValue(normalized);
            row.setParkingLotId(lotId);
            row.setParamLevel(ParamKeys.LEVEL_LOT);
            row.setGroupName(def.groupName());
            row.setValueType(def.valueType());
            row.setOptions(def.options());
            row.setDescription(def.description());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            configMapper.insert(row);
        }

        cache.evict(lotKey(lotId, key));
        log.info("车场参数已设置: lotId={} key={} value={}", lotId, key, normalized);
    }

    /**
     * 重置车场级参数为"继承全局/默认"（物理删除该车场的覆盖行）。
     * <p>
     * <strong>不做权限校验</strong>，调用方须先完成角色/车场范围鉴权。
     *
     * @param lotId 车场 ID
     * @param key   参数键
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetLotParam(Long lotId, String key) {
        if (lotId == null || lotId <= ParamKeys.GLOBAL_LOT_ID) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "车场 ID 非法");
        }
        if (!ParamKeys.isLotOverridable(key)) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "不支持的车场级参数: " + key);
        }
        configMapper.delete(new QueryWrapper<SysConfig>()
                .eq("config_key", key)
                .eq("parking_lot_id", lotId));
        cache.evict(lotKey(lotId, key));
        log.info("车场参数已重置为继承: lotId={} key={}", lotId, key);
    }

    /**
     * 失效某全局参数的缓存层。
     * <p>
     * 供 {@code SystemParamService} 在更新全局参数后调用，使所有回退到全局的车场立即生效。
     *
     * @param key 参数键
     */
    public void evictGlobal(String key) {
        if (key != null) {
            cache.evict(globalKey(key));
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 读取某一层（车场层或全局层）的原始值，带缓存与"缺省哨兵"防穿透。
     *
     * @param cacheKey     缓存键
     * @param parkingLotId 该层对应的 parking_lot_id（车场层=车场ID，全局层=0）
     * @param key          参数键
     * @return 该层的原始值；该层无记录时返回 {@code null}
     */
    private String readLayer(String cacheKey, long parkingLotId, String key) {
        String cached = cache.get(cacheKey);
        if (cached != null) {
            return NULL_SENTINEL.equals(cached) ? null : cached;
        }
        SysConfig row = configMapper.selectOne(new QueryWrapper<SysConfig>()
                .eq("config_key", key)
                .eq("parking_lot_id", parkingLotId));
        String value = (row != null) ? row.getConfigValue() : null;
        cache.put(cacheKey, value != null ? value : NULL_SENTINEL);
        return value;
    }

    /**
     * 校验并规范化参数值。
     */
    private String validateAndNormalize(ParamKeys.Definition def, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "参数值不能为空: " + def.key());
        }
        String trimmed = value.trim();
        switch (def.valueType()) {
            case ParamKeys.TYPE_INT -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    throw new BusinessException(CommonErrorCode.PARAM_ERROR, "参数需为整数: " + def.key());
                }
                if (parsed < 0) {
                    throw new BusinessException(CommonErrorCode.PARAM_ERROR, "参数不能为负数: " + def.key());
                }
                return String.valueOf(parsed);
            }
            case ParamKeys.TYPE_BOOLEAN -> {
                if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
                    throw new BusinessException(CommonErrorCode.PARAM_ERROR, "参数需为 true/false: " + def.key());
                }
                return trimmed.toLowerCase();
            }
            case ParamKeys.TYPE_ENUM -> {
                if (def.options() == null || !def.options().contains("\"" + trimmed + "\"")) {
                    throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                            "参数取值超出允许范围: " + def.key() + "，允许值=" + def.options());
                }
                return trimmed;
            }
            default -> {
                return trimmed;
            }
        }
    }

    private String lotKey(long lotId, String key) {
        return "lot:" + lotId + ":" + key;
    }

    private String globalKey(String key) {
        return "global:" + key;
    }
}
