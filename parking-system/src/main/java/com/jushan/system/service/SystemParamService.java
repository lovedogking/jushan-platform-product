package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.redis.CacheNames;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.dto.SystemParamUpdateRequest;
import com.jushan.system.entity.SysConfig;
import com.jushan.system.mapper.SysConfigMapper;
import com.jushan.system.vo.SystemParamVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统参数管理服务（Phase 1 A3）。
 * <p>
 * 封装 sys_config 表的读写操作，支持：
 * <ul>
 *   <li>分组查询所有参数（供前端展示）</li>
 *   <li>按 key 查询单个参数详情</li>
 *   <li>修改参数值（清除 Redis 缓存）</li>
 *   <li>供其他服务读取参数值（{@link #getValue(String)}）</li>
 * </ul>
 * <p>
 * <strong>缓存策略</strong>：
 * <ul>
 *   <li>{@code getValue(key)} 使用 {@code @Cacheable(cacheNames = CacheNames.SYS_DICT)}，减少数据库查询</li>
 *   <li>{@code updateParam(key, value)} 使用 {@code @CacheEvict} 清除对应缓存</li>
 *   <li>全量查询不缓存（sys_config 表极小，全表扫描性能可忽略）</li>
 * </ul>
 * <p>
 * <strong>注意</strong>（任务包 1-1）：{@code sys_config} 现为参数分层存储（全局行 {@code parking_lot_id=0}）。
 * 本服务只管理<b>全局参数</b>（系统参数页仅展示全局）；车场级读写由 {@code ParamResolver} 统一处理。
 * 全局参数更新后会同步失效 {@code ParamResolver} 的全局缓存层，使回退到全局的车场立即生效。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class SystemParamService {

    private static final Logger log = LoggerFactory.getLogger(SystemParamService.class);

    private final SysConfigMapper configMapper;
    private final ParamResolver paramResolver;

    public SystemParamService(SysConfigMapper configMapper, ParamResolver paramResolver) {
        this.configMapper = configMapper;
        this.paramResolver = paramResolver;
    }

    // ==================== 查询 ====================

    /**
     * 查询所有系统参数（分组排序）。
     * <p>
     * 返回按 groupName 排序的平坦列表，前端按 {@code groupName} 字段分组展示。
     *
     * @return 系统参数列表
     */
    public List<SystemParamVO> getAllParams() {
        List<SysConfig> configs = configMapper.selectList(
                new QueryWrapper<SysConfig>()
                        .eq("parking_lot_id", ParamKeys.GLOBAL_LOT_ID)
                        .orderByAsc("group_name", "config_key"));

        return configs.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 查询单个系统参数详情（含分组和说明）。
     *
     * @param key 参数键
     * @return 参数详情
     */
    public SystemParamVO getParam(String key) {
        SysConfig config = getByKeyOrThrow(key);
        return toVO(config);
    }

    /**
     * 读取参数原始值（供其他服务内部调用）。
     * <p>
     * 此方法使用 {@code @Cacheable} 缓存，key 模式为 {@code sys:param:<configKey>}。
     * 修改参数值时会自动失效。
     *
     * @param key 参数键
     * @return 参数值字符串；参数不存在时返回 {@code null}
     */
    @Cacheable(cacheNames = CacheNames.SYS_DICT, key = "'sys:param:' + #key", unless = "#result == null")
    public String getValue(String key) {
        SysConfig config = configMapper.selectOne(
                new QueryWrapper<SysConfig>()
                        .eq("config_key", key)
                        .eq("parking_lot_id", ParamKeys.GLOBAL_LOT_ID));
        if (config == null) {
            log.warn("系统参数不存在，返回 null: key={}", key);
            return null;
        }
        return config.getConfigValue();
    }

    // ==================== 修改 ====================

    /**
     * 更新系统参数值。
     * <p>
     * 修改成功后，清除该参数在 Redis 中的缓存。
     *
     * @param key     参数键
     * @param request 更新请求（含新值）
     * @return 更新后的参数详情
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.SYS_DICT, key = "'sys:param:' + #key")
    public SystemParamVO updateParam(String key, SystemParamUpdateRequest request) {
        SysConfig config = getByKeyOrThrow(key);

        String oldValue = config.getConfigValue();
        String newValue = request.getValue();

        config.setConfigValue(newValue);
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);

        // 同步失效车场级解析器的全局缓存层，使所有回退到全局的车场立即拿到新值
        paramResolver.evictGlobal(key);

        log.info("系统参数已更新: key={} oldValue={} newValue={}", key, oldValue, newValue);

        return toVO(config);
    }

    // ==================== 内部方法 ====================

    /**
     * 按 key 查询配置记录，不存在则抛异常。
     */
    private SysConfig getByKeyOrThrow(String key) {
        SysConfig config = configMapper.selectOne(
                new QueryWrapper<SysConfig>()
                        .eq("config_key", key)
                        .eq("parking_lot_id", ParamKeys.GLOBAL_LOT_ID));
        if (config == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "系统参数不存在: " + key);
        }
        return config;
    }

    /**
     * SysConfig → SystemParamVO 转换。
     */
    private SystemParamVO toVO(SysConfig config) {
        SystemParamVO vo = new SystemParamVO();
        vo.setKey(config.getConfigKey());
        vo.setValue(config.getConfigValue());
        vo.setGroupName(config.getGroupName());
        vo.setValueType(config.getValueType());
        vo.setOptions(config.getOptions());
        vo.setDescription(config.getDescription());
        return vo;
    }
}
