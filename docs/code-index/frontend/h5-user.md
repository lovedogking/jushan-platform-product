# 模块：h5-user（H5 车主端）

> **路径**：`h5-user/`
> **技术栈**：Vue 3 · TypeScript · Vite · Vant
> **职责**：面向车主的 H5 轻量缴费页面：输车牌查费 → 支付确认 → 支付结果。
> **最近更新**：2026-08-11（补登 404 页与 catch-all 路由；后端计费已切 `FeeCalculationService`）

---

## 一、目录结构

```
h5-user/src/
├── api/
│   ├── fee.ts          # 查费 API
│   ├── pay.ts          # 支付预下单/状态查询/模拟通知
│   ├── request.ts      # axios 封装
│   └── types.ts        # H5 类型定义
├── components/
│   └── PlateKeyboard.vue   # 车牌专用键盘
├── router/
│   └── index.ts        # 路由配置
├── stores/
│   └── app.ts          # 最近输入车牌缓存
├── styles/
│   └── index.scss      # 全局样式
├── views/
│   ├── home/index.vue  # 首页：车牌输入 + 查费结果
│   ├── error/404.vue   # 404 页
│   └── pay/
│       ├── confirm.vue # 支付确认页
│       ├── mock.vue    # 模拟支付页
│       └── result.vue  # 支付结果页
├── App.vue
└── main.ts
```

---

## 二、关键文件

| 文件 | 职责 |
|---|---|
| `api/fee.ts` | `queryFeeByPlate(plate)` → `POST /api/v1/h5/fee/query` |
| `api/pay.ts` | `preparePay(orderId)`、`queryPayStatus(orderId)`、`notifyPay(orderId, action)` |
| `views/home/index.vue` | 车牌输入 → 查费 → 展示在场记录与费用 → 跳转支付 |
| `views/pay/confirm.vue` | 支付确认：调用 `preparePay`，mock 模式跳转 `mock.vue`，真实模式跳转 P云 `pay_url`（二期实现） |
| `views/pay/mock.vue` | 模拟支付：成功/失败按钮，调用 `notifyPay` |
| `views/pay/result.vue` | 支付结果：根据 URL status 或查询结果显示成功/失败 |
| `components/PlateKeyboard.vue` | 车牌输入专用键盘 |

---

## 三、路由

| 路径 | 页面 | 说明 |
|---|---|---|
| `/` | `home/index.vue` | 查费首页 |
| `/pay/:orderId` | `pay/confirm.vue` | 支付确认 |
| `/mock-pay/:orderId` | `pay/mock.vue` | 模拟支付 |
| `/pay/result` | `pay/result.vue` | 支付结果 |
| `/:pathMatch(.*)*` | `error/404.vue` | 404 兜底（catch-all） |

---

## 四、状态与二期任务

- ✅ 骨架完成：查费、模拟支付闭环。
- 🚧 真实 P云 支付：`confirm.vue` 真实分支当前仅 `showToast('真实支付暂未接入')`，二期需改为跳转 P云 `pay_url`。
- ✅ 后端计费引擎切换：`H5FeeController` / `H5PayController` 已切至 `FeeCalculationService`（原 `BillingEngine` 引用已移除）。
