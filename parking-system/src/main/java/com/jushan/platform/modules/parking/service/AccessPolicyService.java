package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.parking.dto.AccessPolicyCreateCmd;
import com.jushan.platform.modules.parking.entity.AccessPolicy;
import com.jushan.platform.modules.parking.vo.AccessPolicyVO;

import java.util.List;
import java.util.Map;

/**
 * 车辆进出策略配置服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface AccessPolicyService extends IService<AccessPolicy> {

    /**
     * 创建策略配置。
     *
     * @param cmd 创建命令
     * @return 策略视图对象
     */
    AccessPolicyVO create(AccessPolicyCreateCmd cmd);

    /**
     * 更新策略配置。
     *
     * @param id  策略ID
     * @param cmd 更新命令
     * @return 策略视图对象
     */
    AccessPolicyVO update(Long id, AccessPolicyCreateCmd cmd);

    /**
     * 删除策略配置。
     *
     * @param id 策略ID
     */
    void delete(Long id);

    /**
     * 查询策略详情。
     *
     * @param id 策略ID
     * @return 策略视图对象
     */
    AccessPolicyVO detail(Long id);

    /**
     * 分页查询策略列表。
     *
     * @param page         分页参数
     * @param parkingLotId 停车场ID（可选）
     * @param policyType   策略类型（可选）
     * @return 分页结果
     */
    IPage<AccessPolicyVO> pageList(IPage<AccessPolicy> page, Long parkingLotId, String policyType);

    /**
     * 查询指定停车场的所有策略。
     *
     * @param parkingLotId 停车场ID
     * @return 策略列表
     */
    List<AccessPolicyVO> listByParkingLotId(Long parkingLotId);

    /**
     * 查询指定停车场的指定类型策略。
     *
     * @param parkingLotId 停车场ID
     * @param policyType   策略类型
     * @return 策略列表
     */
    List<AccessPolicyVO> listByType(Long parkingLotId, String policyType);

    /**
     * 获取策略键值对 Map（用于快速查询）。
     *
     * @param parkingLotId 停车场ID
     * @param policyType   策略类型
     * @return 策略键值对
     */
    Map<String, String> getPolicyMap(Long parkingLotId, String policyType);
}
