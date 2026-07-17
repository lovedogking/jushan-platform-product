/**
 * 本期业务主战场，受控使用；新功能需对应 V1.1 功能编号，二期统一迁移。
 * <p>
 * 本包承载进出场、计费引擎、订单、模拟支付、设备、车场/车道、月卡/固定车位、
 * 系统参数、岗亭监控等约 80% 核心业务。V1.1 剩余缺口（收费闭环/月卡重建/名单/临牌）
 * 的大多数落在此包业务链路上。
 * <p>
 * 新包 com.jushan.platform.modules 保留已成型模块（auth/account/company/department、
 * device-webhook、miniapp、parking-session/recognition-event）。
 * 新 fee_rule 计费体系代码已实现但本期冻结为二期候选（见 com.jushan.platform.modules.parking）。
 */
package com.jushan.system;
