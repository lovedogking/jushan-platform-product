/**
 * 二期计费体系候选，本期冻结不接入。
 * <p>
 * 本包（com.jushan.platform.modules.parking）下的 fee_rule 计费体系代码已实现，
 * 但未接入实际计费链路。本期以旧 billing_rule 为唯一计费体系，本包代码保留在仓库中
 * 作为二期候选方案。
 * <p>
 * 冻结范围：FeeRuleController、FeeCalculationController、ParkingZoneController、
 * FeeRuleService、FeeCalculationService、ParkingZoneService 及关联的 Entity/Mapper/DTO/VO。
 * <p>
 * 恢复条件：二期计费体系重构启动后，完成以下事项方可解冻：
 * <ol>
 *   <li>完成 fee_rule 计费引擎与 ExitService/ParkingFeeService 的对接</li>
 *   <li>通过全量回归测试（覆盖四种计费模式 + 跨天 + 封顶）</li>
 *   <li>数据迁移：billing_rule → fee_rule 存量规则映射</li>
 *   <li>前端收费规则页切换回 fee-rule API</li>
 * </ol>
 *
 * @see com.jushan.system.service.BillingEngine 本期唯一计费引擎
 * @see com.jushan.system.controller.BillingRuleController 本期收费规则管理入口
 */
package com.jushan.platform.modules.parking;
