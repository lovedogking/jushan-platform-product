export const meta = {
  name: 'prd-to-implementation-plan',
  description: '基于停车SaaS PRD生成可执行开发任务清单、核心设计、DDL、OpenAPI与架构建议的完整Markdown实施计划',
  phases: [
    { title: '生成章节', detail: '6个独立章节并行生成并写入临时文件' },
    { title: '合并文档', detail: '将6个章节合并为带目录的完整实施计划Markdown文件' }
  ]
};

const sectionSchema = {
  type: 'object',
  properties: {
    sectionFile: { type: 'string', description: '写入的章节文件绝对路径' }
  },
  required: ['sectionFile']
};

phase('生成章节');

const outputDir = args.outputDir;
const prdPath = args.prdPath;

const sectionResults = await parallel([
  () => agent(
    `请阅读 PRD 文档 ${prdPath}，生成【第1章：开发任务拆分】。\n` +
    `要求：\n` +
    `1. 按 一期MVP → 二期增值 → 三期生态 三阶段输出开发计划。\n` +
    `2. 每个 Sprint 包含：任务名称、描述、前后端分工、依赖前置任务、预计工时（人天）。\n` +
    `3. 同时列出涉及的数据库表、API接口（路径+方法）、前端页面/组件。\n` +
    `4. 给出核心算法/逻辑要点、测试要点、验收标准。\n` +
    `5. 每个任务用标签标注【前端】/【后端】/【联调】/【测试】。\n` +
    `6. 任务必须按依赖顺序排列，前置任务先完成。\n` +
    `7. 严格基于 PRD 最终确认结果，不扩展未确认功能。\n` +
    `8. 将完整 Markdown 内容写入文件 ${outputDir}/section_1_tasks.md，只返回文件路径。`,
    { label: '1-任务拆分', phase: '生成章节', schema: sectionSchema, model: 'opus' }
  ),
  () => agent(
    `请阅读 PRD 文档 ${prdPath}，生成【第2章：核心算法伪代码】。\n` +
    `必须输出以下5个算法的详细伪代码（结构化文本，含输入/输出/边界条件/异常处理）：\n` +
    `1. 收费规则计算引擎（按时/分时段/节假日/封顶/跨天/优惠券抵扣/积分抵扣）。\n` +
    `2. 一位多车判断逻辑（绑定数量/在场判断/自动升级/续期同步）。\n` +
    `3. 优惠券最优匹配算法（过滤/排序/叠加规则/积分叠加）。\n` +
    `4. 车位余量实时计算（精准/冗余/分时段/并发控制）。\n` +
    `5. 车牌识别匹配逻辑（黑名单/有效期/车位满/通道权限/一位多车/费用计算）。\n` +
    `要求：金额用 BigDecimal，车牌统一大写，所有判断必须带 tenant_id，并发控制说明。\n` +
    `将完整 Markdown 内容写入文件 ${outputDir}/section_2_algorithms.md，只返回文件路径。`,
    { label: '2-算法伪代码', phase: '生成章节', schema: sectionSchema, model: 'opus' }
  ),
  () => agent(
    `请阅读 PRD 文档 ${prdPath}，生成【第3章：数据库DDL】。\n` +
    `至少包含以下核心表的 MySQL 8.0 建表语句：\n` +
    `parking_lot, parking_zone, parking_lane, fee_rule, vehicle, vehicle_multi_plate, vehicle_wallet, vehicle_wallet_log, department, lane_permission, blacklist, coupon, user_coupon, coupon_usage_log, member_points, points_transaction, merchant, merchant_coupon_stock, parking_order, vehicle_access_log, offline_sync_batch, visitor_apply, visitor_unit, audit_flow, admin_account, custom_role, device_camera, device_command_log。\n` +
    `要求：\n` +
    `1. 所有表必须包含 tenant_id 和 deleted_at 软删除字段。\n` +
    `2. 字段必须有 COMMENT。\n` +
    `3. 合理设置主键、索引、外键（软删除场景外键按需）。\n` +
    `4. 金额字段使用 DECIMAL(19,2)，时间用 datetime/timestamp，状态用 tinyint 或 varchar 并注释枚举。\n` +
    `5. 车牌号码字段必须存储大写，查询忽略大小写（可用 varchar + 索引说明）。\n` +
    `6. 操作日志表必须记录操作人、IP、时间、变更前后值 JSON。\n` +
    `7. 将完整 SQL（含 DROP TABLE IF EXISTS 与 CREATE TABLE）写入文件 ${outputDir}/section_3_ddl.md，只返回文件路径。`,
    { label: '3-数据库DDL', phase: '生成章节', schema: sectionSchema, model: 'opus' }
  ),
  () => agent(
    `请阅读 PRD 文档 ${prdPath}，生成【第4章：核心API接口文档（OpenAPI 3.0）】。\n` +
    `选出最关键的20个API接口（如车场CRUD、区域/通道管理、收费规则、车辆入场/出场、费用计算、订单创建/支付/查询、优惠券列表/核销、访客预约、开闸指令等），用标准 OpenAPI 3.0 YAML 格式输出。\n` +
    `每个接口必须包含：路径、HTTP方法、请求参数（含校验规则）、响应结构、错误码、幂等性设计、并发控制说明。\n` +
    `要求：\n` +
    `1. 统一返回结构（code/msg/data）。\n` +
    `2. 多租户接口必须带 tenant_id 或从登录上下文取。\n` +
    `3. 金额字段使用 string 类型（BigDecimal）。\n` +
    `4. 将完整 YAML 写入文件 ${outputDir}/section_4_openapi.md，只返回文件路径。`,
    { label: '4-OpenAPI文档', phase: '生成章节', schema: sectionSchema, model: 'opus' }
  ),
  () => agent(
    `请阅读 PRD 文档 ${prdPath}，生成【第5章：预留扩展接口设计】。\n` +
    `对以下预留功能输出接口数据结构（输入/输出/字段定义），确保当前代码可无缝扩展，并标注【预留】：\n` +
    `1. 第三方支付（支付宝/银联）支付回调。\n` +
    `2. 无感支付（微信免密/支付宝无感）签约/扣款。\n` +
    `3. 相机指令下发（抬杆/落杆/显示屏文字/语音播报/固件升级）。\n` +
    `4. 第三方充电桩API对接（充电记录获取）。\n` +
    `5. 开放API（OpenAPI网关认证/限流/版本管理）。\n` +
    `要求：\n` +
    `1. 当前返回 mock 数据或空实现，但数据结构必须完整。\n` +
    `2. 包含请求/响应字段、数据类型、必填、示例、枚举说明。\n` +
    `3. 将完整 Markdown 内容写入文件 ${outputDir}/section_5_reserved.md，只返回文件路径。`,
    { label: '5-预留扩展接口', phase: '生成章节', schema: sectionSchema, model: 'opus' }
  ),
  () => agent(
    `请阅读 PRD 文档 ${prdPath}，生成【第6章：技术架构建议】。\n` +
    `基于以下技术栈给出架构建议：\n` +
    `后端：Java + Spring Boot + MyBatis-Plus + MySQL 8.0 + Redis\n` +
    `前端PC：Vue 3 + Element Plus\n` +
    `前端岗亭：Vue 3 + PWA\n` +
    `小程序：微信小程序原生\n` +
    `部署：Docker + K8s\n` +
    `必须包含：服务拆分建议、数据库分库分表策略、Redis缓存策略、消息队列使用场景、并发控制方案（车位余量/优惠券库存/积分扣减）、分布式锁实现、数据归档方案。\n` +
    `要求：结合 PRD 三端一体架构和SaaS多租户，标注预留扩展。\n` +
    `将完整 Markdown 内容写入文件 ${outputDir}/section_6_architecture.md，只返回文件路径。`,
    { label: '6-技术架构建议', phase: '生成章节', schema: sectionSchema, model: 'opus' }
  )
]);

phase('合并文档');

const sectionFiles = sectionResults.filter(Boolean).map(r => r.sectionFile);

const mergeResult = await agent(
  `请将以下6个章节文件合并为一个完整的 Markdown 实施计划文档：\n` +
  sectionFiles.join('\n') +
  `\n\n合并要求：\n` +
  `1. 输出文件路径：${outputDir}/停车SaaS_PRD实施计划_V1.0.md。\n` +
  `2. 文档开头包含：标题、版本、日期、PRD来源、总体说明、目录。\n` +
  `3. 按 第1章→第2章→第3章→第4章→第5章→第6章 顺序拼接，保留各章节原有标题层级。\n` +
  `4. 在开头添加一段"文档说明"，强调本计划基于 PRD V1.0 最终定稿，所有实现必须遵循 SaaS 多租户、软删除、BigDecimal 金额、车牌大写、操作日志、并发控制等约束。\n` +
  `5. 将最终合并后的完整 Markdown 内容写入上述输出文件，只返回输出文件路径。`,
  { label: '合并文档', phase: '合并文档', schema: sectionSchema, model: 'opus' }
);

return mergeResult;
