# 09 — Client 层迁移（15 个）

**Type:** task
**Status:** resolved
**Blocked by:** none

## 范围

将 `com.jushan.system.client.*` 下设备接入客户端相关代码迁至 `modules/device/client/`：

| 文件 | 说明 |
|---|---|
| `DeviceAccessClient.java` | 设备接入客户端接口 |
| `DeviceAccessClientImpl.java` | HTTP 实现（822 行） |
| `MockDeviceAccessClient.java` | Mock 实现（测试用） |
| `WeChatApiClient.java` | 微信 API 客户端（425 行，属 miniapp 域） |

以及 11 个 DTO → `modules/device/client/dto/`：
`CaptureResultDTO`, `CommandResultDTO`, `DeviceAccessResponse`, `DeviceStatusDTO`, `DisplayConfigRequest`, `DisplayResultDTO`, `DisplaySaveRequest`, `DisplayTextRequest`, `TimeSyncResultDTO`, `VoiceControlRequest`, `VoiceResultDTO`

## 关键风险

- `DeviceService` 和 `BoothMonitorService` 等大量引用 `DeviceAccessClient`
- `WeChatApiClient` 被 `WxUserService`、`MiniAuthService` 引用，应迁至 `modules/miniapp/client/`
- 11 个 DTO 结构简单，批量搬移风险低

## 步骤

1. Device 相关：`DeviceAccessClient` + Impl + Mock + 11 DTO → `modules/device/client/`
2. 微信相关：`WeChatApiClient` → `modules/miniapp/client/`
3. 全局替换 import
4. 编译 + 测试

## Comments
