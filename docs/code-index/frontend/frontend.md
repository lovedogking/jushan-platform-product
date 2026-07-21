# 模块：frontend（岗亭端前端）

> **路径**：`frontend/` （独立 `pnpm` 工作区）
> **技术栈**：Vue 3.4 · TypeScript 5.4 · Vite 5.2 · Pinia 2.1 · ant-design-vue 4.1 · axios
> **最近更新**：2026-07-21

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
    ├── monitor/         # 监控页（4 个文件）
    ├── operation/       # 运营（5 个文件）
    ├── admin/           # 管理
    └── error/           # 错误页
```

---

## 二、关键文件

| 文件 | 职责 |
|---|---|
| `api/charge.ts` | 核心收费 API：`getChargeInfo`(查在场)、`submitCharge`(收费出场)、`manualOpenGate`/`manualCloseGate`/`manualLockGate`/`manualUnlockGate`（道闸控制）、`getCurrentFeeRule`/`updateFeeRule`（规则调整）、`submitFeeReduction`（减免） |
| `api/monitor.ts` | 监控 API：快照、设备状态刷新、异常提醒确认 |
| `utils/request.ts` | axios 实例 + 请求/响应拦截（Token 注入、401 跳登录） |
| `utils/websocket.ts` | STOMP over WebSocket（接收实时监控数据） |
| `utils/offline-queue.ts` | 离线时缓存请求到 localStorage 并在恢复时重放 |
| `stores/monitor.ts` | 中心化监控状态（设备列表、在场车辆、余位、异常） |
| `views/monitor/` | 监控大屏主页面（实时设备/车位/事件/异常面板） |
| `.env.development` / `.env.production` | 环境变量（API base URL / WebSocket） |
