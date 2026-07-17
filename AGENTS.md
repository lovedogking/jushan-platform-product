# AGENTS.md — 停车SaaS系统

## 项目概述
多租户停车SaaS管理平台，支持运营端(Web)、岗亭端(Web)、小程序端(微信)三端，通过适配器层对接停车场硬件设备。

## 包架构说明
- **com.jushan.system** — 本期业务主战场，受控使用。承载进出场、计费引擎、订单、模拟支付、设备、车场/车道、月卡/固定车位、系统参数、岗亭监控等约 80% 核心业务。V1.1 新功能需对应功能编号标注，二期统一迁移至 com.jushan.platform.modules。
- **com.jushan.platform.modules** — 已成型模块（auth/account/company/department、device-webhook、miniapp、parking-session/recognition-event）。
- **com.jushan.platform.modules.parking** — fee_rule 计费体系代码已实现但本期冻结为二期候选。本期以旧 billing_rule 为唯一计费体系。

## 技术栈
- 后端: Java 21 + Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Flyway
- 前端: Vue 3 + Ant Design Vue (admin-web/booth-web)
- 小程序: 微信小程序原生
- 通信: HTTP REST (平台↔适配器) + MQTT (适配器↔设备) + WebSocket (岗亭实时推送)
- 中间件: RabbitMQ (平台内部事件总线) + Redis (缓存)

## 构建命令
```bash
# 后端编译
mvn clean compile -pl parking-system -am

# 后端测试
mvn test -pl parking-boot -am

# 后端打包
mvn clean package -pl parking-boot -am

# admin-web 构建
cd admin-web && pnpm build

# booth-web 构建
cd booth-web && pnpm build

# 小程序构建
cd miniapp && npm run build:mp-weixin
```
架构约束
多租户: 所有业务表必须含 tenant_id，MyBatis-Plus TenantLineInnerInterceptor 自动拦截
数据隔离: 超级管理员全平台，租户管理员仅被分配车场，岗亭管理员仅授权车场
通信: 平台→适配器走 HTTP REST，适配器→设备走 MQTT，平台内部识别事件走 RabbitMQ
支付: 本期仅模拟支付，禁止连接任何真实支付平台，MockPaymentService 是唯一支付入口
设备: 开闸/关闸/校时通过 DeviceAccessClient HTTP REST 下发，事件通过 Webhook 接收
代码规范
所有实体类必须含 tenant_id, created_at, updated_at, deleted_at
金额统一用 BigDecimal（数据库 DECIMAL(10,2)），禁止 float/double
时间统一用 LocalDateTime，禁止 java.util.Date
所有 Controller 必须加 @RequirePermission 权限注解
敏感操作（开闸/收费规则修改/手动开闸）必须加 @BusinessLog 记录操作日志
数据库变更必须用 Flyway 迁移，命名 V{日期序号}__{描述}.sql
接口返回统一格式 {code, message, data, timestamp}
异常统一全局处理，Controller 中禁止写 try-catch
安全红线
禁止生产环境加载 InternalGateController（已标记 @Profile("dev")）
禁止 pyun.mock=false（真实支付已物理切断）
禁止前端传入 deviceSn 直接操作设备，必须通过 laneId 查询数据库可信记录
禁止未授权访问其他租户数据，所有查询必须带 tenant_id 或 ParkingLotScopeResolver
禁止在 Controller 中写 SQL 拼接，所有查询通过 MyBatis-Plus 或 @Query JPQL
测试要求
单元测试覆盖率 ≥ 70%（核心业务模块）
新增功能必须附带单元测试
提交前必须运行 mvn test 确保通过
已有失败测试（RecognitionEventServiceImplTest、DeviceWebhookControllerTest）为已知问题，不阻塞提交
模块结构
plain
parking-boot/          # 启动模块 + Flyway 迁移 + 集成测试
├── parking-system/    # 业务模块 (~13,500+ 行)
├── parking-infrastructure/  # 安全/JWT/权限/租户/日志
├── parking-framework/ # Redis/MQ/WebSocket/分布式锁
└── parking-common/    # BaseEntity/R/ErrorCode
admin-web/             # 运营端 Vue 3
booth-web/             # 岗亭端 Vue 3
miniapp/               # 微信小程序
关键文件
parking-boot/src/main/resources/application.yml — 主配置（pyun.mock 必须 true）
parking-system/src/main/java/com/jushan/system/service/MockPaymentService.java — 模拟支付核心
parking-system/src/main/java/com/jushan/system/service/EntryService.java — 入场核心
parking-system/src/main/java/com/jushan/system/service/ExitService.java — 出场核心
parking-infrastructure/.../TenantLineInnerInterceptor.java — 多租户拦截器
plain
