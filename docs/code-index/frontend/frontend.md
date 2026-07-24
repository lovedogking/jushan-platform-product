# 模块：frontend（岗亭端前端）

> **路径**：`frontend/` （独立 `pnpm` 工作区）
> **技术栈**：Vue 3.4 · TypeScript 5.4 · Vite 5.2 · Pinia 2.1 · ant-design-vue 4.1 · axios
> **最近更新**：2026-07-24（v1.5：MIXED 车道拆分入口/出口两张卡片，常关按钮 lockKey 拆分，常关/取消常关独立端点）

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
    ├── monitor/         # 监控页（5 个文件）
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
| `views/monitor/` | 监控大屏主页面（实时设备/车位/事件/异常面板；识别事件列表与车道卡片实时显示抓拍图；车道卡片按钮按 `gateCapabilities` 接口返回的能力动态渲染——Q8车道只显示「开闸」「常开/取消常开」，C5车道显示「开闸」「关闸」「常开/取消常开」「常关/取消常关」；MIXED 车道按方向拆分为入口/出口两张独立卡片；手动放行弹窗预填最近识别车牌） |
| `views/operation/AccessRecords.vue` | 运营端通行记录页（分页/筛选，含入场抓拍图列） |
| `.env.development` / `.env.production` | 环境变量（API base URL / WebSocket） |
