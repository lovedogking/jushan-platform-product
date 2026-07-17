# 臻识 C5H 相机 GPIO 控制配置

> 文档状态：**ACTIVE**
> 适用范围：臻识 C5H 相机（固件 bv=16771）通过 MQTT GPIO 控制道闸
> 验证日期：2026-07-15（真机联调通过）
> 关联文档：[真机联调准备任务书](../真机联调准备任务书.md)、[一期联调准备清单](./开发计划/一期联调准备清单.md)

---

## 1. 控制架构

```
平台 / 脚本
    ↓ HTTP
Device Access (:8081)
    ↓ MQTT
EMQX Broker (192.168.20.143:1883)
    ↓ MQTT (Topic: device/{sn}/message/down/gpio_out)
臻识 C5H 相机 (192.168.20.222)
    ↓ GPIO 继电器 (IO0 → 开闸, IO1 → 关闸)
道闸控制器
    ↓
闸杆
```

**关键说明**：
- 相机不支持 `gate_direct_open` 命令（固件版本不足 v1.1.14）
- 开闸/关闸通过 `gpio_out` 命令控制 GPIO 引脚实现
- MQTT Broker 使用独立 EMQX，不是相机自身

---

## 2. GPIO 引脚接线

| 相机端口 | 连接目标 | 信号类型 | 说明 |
|---------|---------|---------|------|
| **GPIO COM** | 道闸控制器 COM | 公共端 | 共用地线 |
| **GPIO IO0** | 道闸控制器 OPEN | 开闸信号 | 脉冲触发抬杆 |
| **GPIO IO1** | 道闸控制器 CLOSE | 关闸信号 | 脉冲触发落杆 |

> 注意：若道闸控制器只有 OPEN/COM 两根线（无 CLOSE），则关闸依赖道闸自动落杆功能。

---

## 3. MQTT 命令格式

### 3.1 Topic

```
device/{deviceSn}/message/down/gpio_out
```

- `{deviceSn}`：相机的设备序列号（如 `917e2298-8ddf3e46`）
- QoS：1

### 3.2 开闸命令

```json
{
    "id": "唯一消息ID",
    "sn": "917e2298-8ddf3e46",
    "name": "gpio_out",
    "version": "1.0",
    "timestamp": 1784098020,
    "payload": {
        "type": "gpio_out",
        "body": {
            "delay": 1500,
            "io": 0,
            "value": 2
        }
    }
}
```

| 参数 | 值 | 说明 |
|------|-----|------|
| `io` | `0` | GPIO 端口 0（开闸信号线） |
| `value` | `2` | 先通后断（脉冲模式） |
| `delay` | `1500` | 脉冲宽度 1500ms |

### 3.3 关闸命令

```json
{
    "id": "唯一消息ID",
    "sn": "917e2298-8ddf3e46",
    "name": "gpio_out",
    "version": "1.0",
    "timestamp": 1784098020,
    "payload": {
        "type": "gpio_out",
        "body": {
            "delay": 3000,
            "io": 1,
            "value": 2
        }
    }
}
```

| 参数 | 值 | 说明 |
|------|-----|------|
| `io` | `1` | GPIO 端口 1（关闸信号线） |
| `value` | `2` | 先通后断（脉冲模式） |
| `delay` | `3000` | 脉冲宽度 3000ms |

### 3.4 回执格式

相机回复 Topic：`device/{sn}/message/down/gpio_out/reply`

```json
{
    "code": 200,
    "id": "对应消息ID",
    "name": "gpio_out",
    "payload": null,
    "sn": "917e2298-8ddf3e46",
    "timestamp": 1784097917,
    "version": "1.0"
}
```

- `code=200`：执行成功
- `code≠200`：执行失败

---

## 4. GPIO 参数参考

### 4.1 `io` 端口选择

| 值 | 说明 | 联调验证 |
|----|------|---------|
| `0` | 开闸信号（道闸 OPEN） | ✅ 已验证 |
| `1` | 关闸信号（道闸 CLOSE） | ✅ 已验证 |
| `2` | 备用 | ❌ 未接线 |
| `3` | 备用 | ❌ 未接线 |

### 4.2 `value` 信号类型

| 值 | 含义 | 说明 |
|----|------|------|
| `0` | 断开 | GPIO 输出低电平 |
| `1` | 常通 | GPIO 输出持续高电平 |
| `2` | 先通后断 | GPIO 输出脉冲（通→delay ms→断）|
| `3` | 保留 | 未测试 |

### 4.3 `delay` 脉冲宽度

- 取值范围：`[500, 5000]`（毫秒）
- 开闸推荐：`1500ms`
- 关闸推荐：`3000ms`

---

## 5. 快速测试（通过 Python）

### 安装依赖

```bash
pip3 install paho-mqtt
```

### 开闸脚本

```python
import paho.mqtt.client as mqtt
import json, time

client = mqtt.Client(client_id="gate_test")
client.connect("192.168.20.143", 1883, 60)

payload = {
    "id": hex(int(time.time()*1000000))[2:],
    "sn": "917e2298-8ddf3e46",
    "name": "gpio_out",
    "version": "1.0",
    "timestamp": int(time.time()),
    "payload": {
        "type": "gpio_out",
        "body": {"delay": 1500, "io": 0, "value": 2}
    }
}
client.publish("device/917e2298-8ddf3e46/message/down/gpio_out", json.dumps(payload))
client.disconnect()
```

### 关闸脚本

```python
import paho.mqtt.client as mqtt
import json, time

client = mqtt.Client(client_id="gate_test")
client.connect("192.168.20.143", 1883, 60)

payload = {
    "id": hex(int(time.time()*1000000))[2:],
    "sn": "917e2298-8ddf3e46",
    "name": "gpio_out",
    "version": "1.0",
    "timestamp": int(time.time()),
    "payload": {
        "type": "gpio_out",
        "body": {"delay": 3000, "io": 1, "value": 2}
    }
}
client.publish("device/917e2298-8ddf3e46/message/down/gpio_out", json.dumps(payload))
client.disconnect()
```

---

## 6. 注意事项

1. **安全**：测试闸杆前确认测试区域无车辆/人员
2. **紧停**：道闸控制器通常有手动按钮或遥控器紧急停止
3. **自动关闸**：部分道闸支持自动落杆（开闸后延时自动关），可通过道闸控制器拨码开关或旋钮设置
4. **无自动重试**：开闸失败不得自动重试，标记 UNCERTAIN 后人工处理
5. **审计**：生产环境所有开闸操作应记录到 `device_command_audit`
6. **DA 限制**：当前 DA 收到 `gpio_out` 回执标记为 `Unhandled`，不影响功能但日志有噪音，后续版本补充处理
