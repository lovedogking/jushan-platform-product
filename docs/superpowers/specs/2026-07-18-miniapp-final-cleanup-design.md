# 设计文档：小程序收尾（任务包 5-3）

> 需求依据：MINI-007/009/010/011、确认项 81/83/84/85

---

## 1. 概述

任务包 5-3 包含 6 个独立子项，收尾小程序端所有剩余功能：

| # | 子项 | 目标 |
|---|------|------|
| 1 | 三标签停车记录页 | 在场中 / 待支付 / 已完成 三标签 + 合并重复页面 |
| 2 | 订阅消息 | 真实微信订阅消息推送（替代 mock 记日志） |
| 3 | 支付统一 | 移除 wx.requestPayment，全链路模拟支付 |
| 4 | 发票入口 | 框架占位页 |
| 5 | 车辆绑定上限 | bind_limit_per_user 从 5→3 + 强制校验 |
| 6 | 接口路径统一 | /wx/* → /api/v1/mini/* |

---

## 2. 子项 1：三标签停车记录页

### 2.1 现状
- `pages/records/records`：单列表，无三标签
- `pages/parking/parking`：功能重叠，含车牌筛选

### 2.2 改造

**后端**（MiniUserController 扩展）：
- `GET /api/v1/mini/parking-records?tab=in_progress|pending_pay|completed&current=&size=`
- tab=in_progress → status=IN（在场中）
- tab=pending_pay → 待支付 + 欠费中（pay_status=UNPAID 或 status=ARREARS），含 order 信息
- tab=completed → 已完成（已支付/已出场）+ 代缴记录（当前用户作为代缴人的 proxy_pay_record）

**前端**（保留 `pages/records/records`，移除 `pages/parking/parking`）：
- 顶部三标签切换
- 在场中：车牌、车场、入场时间、已停时长
- 待支付（含欠费）：车牌、金额、入场时间，欠费订单标识"欠费"标签，点击进补缴
- 已完成：车牌、金额、入场/出场时间，代缴记录标识"代缴"标签
- 移除 `pages/parking/parking` 从 app.json，清理对应目录

---

## 3. 子项 2：订阅消息

### 3.1 后端

**WeChatApiClient 扩展**：新增 `sendSubscribeMessage(openid, templateId, data)`
- 调 `POST https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=TOKEN`
- access_token 复用已有 `getAccessToken()`
- 错误码 43101（用户拒收）/ 41030（模板不存在）→ 仅 log.warn，不抛异常

**MiniMessageServiceImpl 改造**：
- `sendSubscribeMessage()` 从 mock 改为调用 `WeChatApiClient.sendSubscribeMessage()`
- 代缴场景推双方：`createPaySuccessMessage(payerUserId, ...)` + `createPaySuccessMessage(ownerUserId, ...)`
- 发送失败降级站内消息（mini_message 表已写入）

**配置**（application.yml）：
```yaml
wx:
  miniapp:
    subscribe-template-id: ${WX_SUBSCRIBE_TEMPLATE_ID:}
```

### 3.2 前端

- 支付成功后调用 `wx.requestSubscribeMessage({ tmplIds: [...] })` 引导授权
- 未授权不阻塞支付流程

---

## 4. 子项 3：支付统一

### 4.1 pay.js 改造

移除：
- `wx.requestPayment` 调用（约 20 行）
- Mock 回退逻辑（`!prepayParams.timeStamp → resolve`）

替换为：
- 预下单 → 展示订单信息 → "确认支付"按钮 → `POST /api/v1/mini/pay/notify` → 完成

### 4.2 后端

- `MiniPayResultVO.prepayParams` 字段保留但不再填充（兼容性）
- 月卡/固定车位已在 5-2 实现纯模拟支付
- 代缴走现有 `/api/v1/mini/pay/proxy-pay`（已是模拟）

---

## 5. 子项 4：发票入口

**新增** `pages/invoice/invoice`（框架占位页）：
- 标题"电子发票"
- 展示"功能即将上线" + 预留表单 UI（抬头/税号输入框 disabled）
- 不接后端 API
- app.json 注册 + 个人中心添加入口

---

## 6. 子项 5：车辆绑定上限

### 6.1 Flyway 订正

新 migration：`UPDATE sys_config SET config_value='3' WHERE config_key='vehicle.bind_limit_per_user' AND config_value='5'`

### 6.2 后端新增 `GET /api/v1/mini/plates`

**MiniUserController 新增接口**：
```java
@GetMapping("/plates")
@RequirePermission("miniapp:view")
public R<List<PlateBindingVo>> listPlates() {
    // 返回当前用户全部已绑定车牌，含 bindingId/plate/vehicleType/isDefault/verifyStatus
}
```

### 6.3 绑定上限校验

`WxUserService.bindPlate()` 中增加参数兜底：
```java
// 现有 bindingPolicy.getMaxBindingsPerUser() 读 binding_policy 表
// 新增兜底：如果 binding_policy 没配，读 sys_config vehicle.bind_limit_per_user
int maxBindings = paramResolver.getInt("vehicle.bind_limit_per_user", null, 3);
```

### 6.4 前端

`plate.js` 绑定前检查当前车牌数 >= maxBindings → 提示"绑定数量已达上限（3辆）"

---

## 7. 子项 6：接口路径统一

### 7.1 前端迁移

| 旧路径 | 新路径 | 调用方 |
|--------|--------|--------|
| `/wx/plates` | `/api/v1/mini/plates` | plate.js |
| `DELETE /wx/plates/{id}` | `DELETE /api/v1/mini/plates/{id}` | plate.js |
| `PUT /wx/plates/{id}/default` | `PUT /api/v1/mini/plates/{id}/default` | plate.js |
| `/wx/user` | `/api/v1/mini/user` | app.js |
| `/wx/login` | `/api/v1/mini/login` | app.js（5-1 已改） |

### 7.2 后端兼容

- 新增 `GET /api/v1/mini/plates` + `DELETE /api/v1/mini/plates/{id}` + `PUT /api/v1/mini/plates/{id}/default` 到 MiniUserController
- 新增 `GET /api/v1/mini/user` 到 MiniUserController
- 旧 `/wx/*` 路径保留不删（兼容期），但前端不再调用
- SecurityConfig 白名单保持一致

---

## 8. 文件影响清单

### 后端

| 文件 | 操作 | 子项 |
|------|------|------|
| `MiniUserController.java` | **改造** | 1（tab筛选）、6（plates/user接口） |
| `WeChatApiClient.java` | **改造** | 2（sendSubscribeMessage） |
| `MiniMessageServiceImpl.java` | **改造** | 2（真实发送） |
| `MiniPayController.java` | **改造** | 3（移除 prepayParams 填充逻辑） |
| `MiniPayResultVO.java` | **改造** | 3（prepayParams 标记废弃） |
| `WxUserService.java` | **改造** | 5（绑定上限参数兜底） |
| `application.yml` | **改造** | 2（subscribe-template-id 配置） |
| Flyway migration | **新增** | 5（bind_limit 5→3 订正） |

### 前端

| 文件 | 操作 | 子项 |
|------|------|------|
| `pages/records/records.*` | **改造** | 1（三标签） |
| `pages/parking/parking.*` | **删除** | 1（合并） |
| `app.json` | **改造** | 1（移除parking）、4（注册invoice） |
| `pages/pay/pay.js` | **改造** | 3（移除wx.requestPayment） |
| `pages/invoice/invoice.*` | **新增 4 文件** | 4 |
| `pages/plate/plate.js` | **改造** | 5（上限检查）、6（路径迁移） |
| `pages/profile/profile.js` | **改造** | 4（发票入口） |
| `app.js` | **改造** | 6（/wx/user → /api/v1/mini/user） |

---

## 9. 验收标准

1. 三标签记录页可用，欠费订单可补缴，代缴记录归属正确
2. 订阅消息授权引导出现，后端真实发送接口可用，失败降级站内消息
3. 全支付链路无 `wx.requestPayment` 调用
4. 绑定第 4 辆车被拒并提示上限
5. 前端所有 `/wx/*` 调用已迁移到 `/api/v1/mini/*`
6. 发票页可访问，展示占位内容
