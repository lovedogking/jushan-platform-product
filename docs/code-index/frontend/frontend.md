# 模块：frontend（岗亭端前端）

> **路径**：`frontend/` （独立 `pnpm` 工作区）
> **技术栈**：Vue 3.4 · TypeScript 5.4 · Vite 5.2 · Pinia 2.1 · ant-design-vue 4.1 · axios
> **最近更新**：2026-07-25（v2.2：岗亭 UI 完全重构——双层导航+四宫格(入口/出口/车辆详情/通行记录)+右侧窄侧边栏；控闸按钮改为开闸/关闸/道闸常开/道闸常关四键+二次确认；运营端通行记录表格增加车辆类型/入场车道/出场车道/出场时间/停车时长列+车牌/类型/状态/车道筛选）

---

## 一、目录结构

```
frontend/src/
├── api/            # 后端 API 封装（每个文件对应一个功能域）
│   ├── auth.ts         # 登录/退出/刷新 Token
│   ├── charge.ts       # 收费（查在场/提交收费/开闸/关闸/减免/规则）
│   ├── monitor.ts      # 监控（快照/异常提醒/设备状态/在场/历史）
│   ├── parking-lot.ts  # 停车场查询
│   ├── parking-manage.ts # 停车场管理
│   ├── account.ts      # 账号管理
│   └── vehicle-query.ts # 车辆查询
├── components/     # 业务组件
│   ├── ChargePanel.vue           # 收费面板（计费/支付/开闸）
│   ├── FeeRuleEditModal.vue      # 收费规则编辑弹窗
│   ├── ManualReleaseModal.vue    # 人工放行弹窗
│   ├── PlateCorrectionModal.vue  # 车牌纠正弹窗
│   └── TempPlateAlertModal.vue   # 临时车牌提醒弹窗
├── composables/    # 组合式 API
│   ├── useChart.ts        # 图表（ECharts）
│   └── useNetworkStatus.ts # 网络状态（WebSocket + 离线队列）
├── layout/         # 布局
│   └── UnifiedLayout.vue  # 统一布局（含监控/收费/历史 tab）
├── router/         # 路由
│   └── index.ts
├── stores/         # Pinia 状态管理
│   └── monitor.ts  # 监控 Store（设备/车位/异常）
├── styles/         # 样式
│   ├── global.scss / index.scss / variables.scss
├── utils/          # 工具
│   ├── request.ts      # axios 封装（拦截器/重试）
│   ├── websocket.ts    # WebSocket STOMP 客户端
│   └── offline-queue.ts # 离线请求队列
└── views/          # 页面
    ├── login/           # 登录页
    ├── monitor/         # 监控页（4 个文件；index.vue 不再使用 MonitorTabs/ParkingLotSidebar）
    ├── operation/       # 运营（5 个文件）
    ├── admin/           # 管理
    └── error/           # 错误页
```

---

## 二、关键文件

| 文件 | 职责 |
|---|---|
| `api/charge.ts` | 核心收费 API：`getChargeInfo`(查在场)、`submitCharge`(收费出场)、`manualOpenGate`/`manualCloseGate`/`manualLockGate`/`manualUnlockGate`（道闸控制）、`captureImage`（手动抓拍）、`getCurrentFeeRule`/`updateFeeRule`（规则调整）、`submitFeeReduction`（减免）、**`getGateCapabilities`（车道控闸能力查询，驱动按钮渲染）** |
| `api/monitor.ts` | 监控 API：快照、设备状态刷新、异常提醒确认 |
| `api/vehicle-query.ts` | 车辆查询 API：在场车辆、历史通行记录（含入场抓拍图 entryImage）、通行记录分页 |
| `components/ManualReleaseModal.vue` | 人工放行弹窗（放行车辆可编辑、是否计费、放行原因必填；新增「抓拍」按钮调用 captureImage 并预览；确认后调 manualOpenGate，携带 entryImage） |
| `utils/request.ts` | axios 实例 + 请求/响应拦截（Token 注入、401 跳登录） |
| `utils/websocket.ts` | STOMP over WebSocket（接收实时监控数据） |
| `utils/offline-queue.ts` | 离线时缓存请求到 localStorage 并在恢复时重放 |
| `stores/monitor.ts` | 中心化监控状态（设备列表、在场车辆、余位、异常） |
| `views/monitor/index.vue` | **岗亭工作区主页面**。布局：顶部单层导航（飓山智慧停车+岗亭工作区+在线圆点+车场下拉+时间+用户名+退出）→ 常开/常关横幅 → 左85%四宫格（入口车道控制/出口车道控制/车辆详情/通行记录表格）+ 右15%侧边栏（提示消息+车场总车位/在场车辆/剩余车位）。入口/出口车道卡片：车道下拉选择+在线/离线+浅灰视频占位+开闸(绿)/关闸(红)/道闸常开(蓝,二次确认)/道闸常关(橙,二次确认)四按钮。车辆详情卡片：本次识别/上次识别两张抓拍图(可点击放大)+方向+车牌+类型(固定车/临时车)+车道+区域+时间+计费+金额+车主+备注+提示。通行记录表格：车牌号+类型(固定车/临时车标签)+入场时间+出场时间+状态(在场/已出场)+入场图片(可点击放大)，数据源=getParkingSessions。设备在线含120s宽限期防抖。保留收费面板/人工放行/规则编辑/远程开闸/车牌校正弹窗。 |
| `views/monitor/MonitorTabs.vue` | （遗留）原通行监控/车辆查询标签页组件，已从 index.vue 移除引用，保留以备后续复用 |
| `views/monitor/ParkingLotSidebar.vue` | （遗留）原停车场左侧栏组件，已从 index.vue 移除引用，车场选择改为顶部下拉 |
| `views/monitor/VehicleQuery.vue` | （遗留）原车辆查询页（在场车辆+历史记录+分页筛选），已从 MonitorTabs 移除引用 |
| `views/operation/AccessRecords.vue` | 运营端通行记录页。筛选：车牌号/车辆类型(固定车/临时车)/状态(在场/已出场)/入场车道 → 查询/重置。表格列：车牌号+车辆类型(固定车/临时车标签)+订单状态(在场/已出场)+停车区域+入场时间+入口车道(真实名称)+出场时间+出场车道(真实名称)+停车时长。数据源：getParkingSessions + getSnapshot(车道名称映射)。车辆类型覆盖后端所有可能值(WHITE/FIXED/FIXED_SPACE/MONTHLY→固定车, TEMP/null→临时车)。 |
| `.env.development` / `.env.production` | 环境变量（API base URL / WebSocket） |
