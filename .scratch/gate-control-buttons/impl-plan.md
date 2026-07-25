# 岗亭控闸按钮 — 实现计划

> 基于 Grill Me 7 项已收敛决策，按步骤和依赖排序。

## 先决条件

| 项目 | 说明 |
|---|---|
| 臻识 C5 能力 | OPEN_GATE, CLOSE_GATE, KEEP_OPEN, KEEP_CLOSE |
| 芊熠 Q8 能力 | OPEN_GATE, KEEP_OPEN |
| DeviceModel 兜底 | `/gate-capabilities` 优先 `Device.capabilities`，空则 fallback `DeviceModel.capabilities` |
| 常关语义 | 持久锁定关闸（对称常开），取消复用 `unlockGate` 恢复 AUTO |

---

## 步骤

### Step 1: DB — DeviceModel capabilities 更新

> 依赖：无

- 更新 `device_model` 表中臻识 C5 行的 `capabilities` 为 `OPEN_GATE,CLOSE_GATE,KEEP_OPEN,KEEP_CLOSE`
- 更新/确认芊熠 Q8 行的 `capabilities` 为 `OPEN_GATE,KEEP_OPEN`

**验证**：SQL 查询确认两行数据正确。

---

### Step 2: 后端 — DeviceAccessClient 新增 lockCloseGate

> 依赖：无

**文件**：
- `DeviceAccessClient.java`（接口）：新增两个方法签名
  - `CommandResultDTO lockCloseGate(String deviceSn)`
  - `CommandResultDTO lockCloseGate(String deviceSn, String commandId)`
- `DeviceAccessClientImpl.java`：实现 HTTP POST `/gate/lock-close`
- `MockDeviceAccessClient.java`：mock 实现

**验证**：主代码编译通过。

---

### Step 3: 后端 — DeviceService 新增 lockCloseGateByLane

> 依赖：Step 2

**文件**：`DeviceService.java`

在 `lockGateByLane` 附近新增：
```java
public CommandResultDTO lockCloseGateByLane(Long laneId, String reason) {
    // 复用 resolveGateDevice + commandId + 审计日志
    // 调用 deviceAccessClient.lockCloseGate(deviceSn, commandId)
}
```

**验证**：主代码编译通过。

---

### Step 4: 后端 — RecognitionEventService 新增 manualLockCloseGate

> 依赖：Step 3

**文件**：
- `RecognitionEventService.java`：新增 `manualLockCloseGate` 方法签名
- `RecognitionEventServiceImpl.java`：实现
  - 调 `deviceService.lockCloseGateByLane(laneId, reason)`
  - 成功后落库 `lane.gateMode = ALWAYS_CLOSE`
  - 失败处理与 `manualLockGate` 一致

**验证**：主代码编译通过。

---

### Step 5: 后端 — RecognitionEventController 新增端点

> 依赖：Step 4

**文件**：`RecognitionEventController.java`

新增：
```java
@PostMapping("/manual-lock-close-gate")
@RequirePermission("booth:operate")
public R<RecognitionResultVO> manualLockCloseGate(@RequestParam Long laneId, @RequestParam String reason) {
    // 调 recognitionEventService.manualLockCloseGate(laneId, operatorId, reason)
}
```

**验证**：主代码编译通过。

---

### Step 6: 后端 — /gate-capabilities 增加 DeviceModel fallback

> 依赖：无

**文件**：`RecognitionEventController.java` 的 `getGateCapabilities` 方法

修改逻辑：`Device.capabilities` 为空/不存在时 → 查 `device.getModelId()` → `DeviceModel.capabilities`。

**验证**：主代码编译通过。

---

### Step 7: 前端 — API 层新增 manualLockCloseGate

> 依赖：Step 5

**文件**：`frontend/src/api/charge.ts`

新增函数：
```typescript
export async function manualLockCloseGate(laneId: number, reason: string): Promise<RecognitionResultVO>
// POST /api/v1/booth/recognition/manual-lock-close-gate?laneId=...&reason=...
```

---

### Step 8: 前端 — 修复常关按钮能力检查

> 依赖：Step 6

**文件**：`frontend/src/views/monitor/index.vue`

- 常关按钮 `v-if` 从 `KEEP_OPEN` 改为 `KEEP_CLOSE`
- `handleToggleLockClose` 中设置常关时分两步：
  1. 先调 `manualCloseGate` 关一次闸
  2. 再调 `manualLockCloseGate` 锁定常关
- 取消常关继续复用 `manualUnlockGate`（恢复 AUTO）

---

### Step 9: 前端 — 双向车道拆分为两张卡

> 依赖：无（纯前端改动）

**文件**：`frontend/src/views/monitor/index.vue` 的 `laneCards` computed

`direction = MIXED` 时改为返回两个 LaneCard：
- 入口卡：`direction: 'ENTRY'`，filter 入口方向的事件和相机
- 出口卡：`direction: 'EXIT'`，filter 出口方向的事件和相机
- 两张卡的 `laneId` 相同，控闸按钮完全一致

**验证**：启动前端，确认 MIXED 车道渲染两张卡，各自独立事件流。

---

### Step 10: 验证

1. `mvn compile -pl parking-system -am -q` — 后端编译通过
2. `mvn test -pl parking-system` — 178 pass / 12 预存错误不变
3. 前端 `npm run build` — 无 TS 错误
4. 手工验证：确认臻识 C5 设备显示四个按钮，芊熠 Q8 显示两个按钮
