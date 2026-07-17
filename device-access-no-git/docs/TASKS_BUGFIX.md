# Bug 修复任务清单

按顺序执行，每完成一个任务编译通过后再进行下一个。

---

## 任务1：MQTT 层基础修复

文件：`device-access-mqtt/src/main/java/com/smartparking/deviceaccess/mqtt/MqttGatewayImpl.java`

### 1.1 修复 rawJson 解析失败阻断后续逻辑

**问题**：`rawJson` 解析失败时 `return;` 导致后续 `msg` 解析和 listener 回调无法执行。

**改法**：
- 当前代码中 `MqttMessage msg = null;` 已经是初始化状态，**无需修改**
- 修改 `rawJson` 解析 catch 块（约第271行）：
  - 删除 `return;`
  - 改为 `log.debug("Failed to parse MQTT message as Map, rawJson will be null. Topic: {}", topic);`
- 确保 `rawJson` 为 `null` 时，跳过 `rawJson` 相关逻辑（如信路通回执关联），但继续执行 `msg` 解析和 `globalListener` 回调
- 第3步（信路通回执关联）增加 `if (rawJson != null)` 保护

### 1.2 修复 messageArrived 同步调用业务逻辑阻塞 Paho 回调线程

**问题**：`listener.onMessage()` 和 `rawListener.onRawMessage()` 在 Paho 回调线程同步执行，阻塞 MQTT 消息接收。

**改法**：
- 在 `pendingFutures` 字段下方添加 `businessExecutor`：
```java
private final ExecutorService businessExecutor = new ThreadPoolExecutor(
    4, 8, 60, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),
    r -> {
        Thread t = new Thread(r);
        t.setName("mqtt-biz-" + t.getId());
        t.setDaemon(true);
        return t;
    },
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```
- **关键**：`pendingFutures.complete(msg)` 必须保留在 Paho 线程同步执行（不能异步化，否则释放等待线程会有延迟）
- 将 `listener.onMessage()` 和 `rawListener.onRawMessage()` 的调用包进 `businessExecutor.execute(() -> { ... })`
- 在 `disconnect()` 方法中，`client.disconnect()` 之前添加 `businessExecutor.shutdown();`

### 1.3 修复 connectComplete 重连回调中阻塞 subscribe

**问题**：`subscribeDefaultTopics()` 中有 `waitForCompletion()`，阻塞 Paho 重连线程。

**改法**：
- 复用 1.2 引入的 `businessExecutor`，不要 `new Thread()`：
```java
if (reconnect) {
    businessExecutor.execute(() -> {
        try {
            subscribeDefaultTopics();
            log.info("Re-subscribed after reconnect");
        } catch (MqttException e) {
            log.error("Re-subscribe failed after reconnect", e);
        }
    });
}
```

---

## 任务2：Handler 层修复

文件：
- `device-access-adapter/src/main/java/com/smartparking/deviceaccess/adapter/zhenshi/ZhenshiMessageHandler.java`
- `device-access-adapter/src/main/java/com/smartparking/deviceaccess/adapter/xinlutong/XinlutongMessageHandler.java`

### 2.1 修复 plateListener 为 null 时车牌事件静默丢失

**问题**：`plateListener` 未注入时，车牌识别事件静默丢失，无告警。

**改法**：
- 在两个 Handler 的 `@PostConstruct init()` 中，注册监听器之后添加：
```java
if (plateListener == null) {
    log.warn("【启动检查】PlateRecognizedListener 未注入，车牌识别事件将静默丢失！请检查 event 模块是否已启用。");
}
```
- 保留现有的 `@Autowired(required = false)` 和 `if (plateListener != null)` 逻辑

### 2.2 修复 lastPlateMap 内存泄漏

**问题**：`ConcurrentHashMap` 无过期机制，长期运行内存泄漏。

**改法**：
- 在 `device-access-adapter/pom.xml` 添加 Caffeine 依赖：
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>
```
- 将 `lastPlateMap` 改为 Caffeine Cache：
```java
private final Cache<String, String> lastPlateMap = Caffeine.newBuilder()
    .expireAfterWrite(7, TimeUnit.DAYS)
    .maximumSize(10000)
    .build();
```
- `put` 改为 `lastPlateMap.put(sn, plate)`
- `get` 改为 `lastPlateMap.getIfPresent(sn)`
- `getLastPlate()` 改为：
```java
public String getLastPlate(String deviceSn) {
    return Optional.ofNullable(lastPlateMap.getIfPresent(deviceSn)).orElse("");
}
```

### 2.3 修复 saveDisplay 阻塞线程

**问题**：`CompletableFuture.supplyAsync()` 默认使用 `ForkJoinPool.commonPool()`，且循环内 `.get()` 阻塞线程。

**改法**：
- 删除 `supplyAsync` 和循环内的 `.get(10, TimeUnit.SECONDS)`
- 创建专用线程池：
```java
private final ExecutorService displayExecutor = Executors.newFixedThreadPool(2, r -> {
    Thread t = new Thread(r);
    t.setName("display-save-" + t.getId());
    t.setDaemon(true);
    return t;
});
```
- 改为链式 `thenCompose` 组合，不阻塞线程：
```java
CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
for (int i = 0; i < lines.size(); i++) {
    final int index = i;
    future = future.thenCompose(prevOk -> {
        if (!prevOk) return CompletableFuture.completedFuture(false);
        byte[] frame = build0x67Frame(...);
        return sendSerialData(...).thenApply(reply -> {
            boolean ok = reply.getCode() != null && reply.getCode() == 200;
            if (index < lines.size() - 1) {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return ok;
        });
    });
}
return future;
```
- 如果嫌 `thenCompose` 太复杂，可以用 `CompletableFuture.supplyAsync(() -> { ... }, displayExecutor)`，但必须在提示词里说明：`.join()` 阻塞的是 `displayExecutor` 的线程，不是 HTTP 线程。`displayExecutor` 只有 2 个线程，`saveDisplay` 并发调用不能超过 2 个。

---

## 任务3：【Core Capability】设备命令接口抽象与异步化

**⚠️ Core Capability — 修改跨模块接口契约。涉及 6 个文件，拆分为子任务。**

### 设计确认

1. 在 `api` 模块新建接口 `DeviceCoordinator`
2. `ZhenshiDeviceCoordinator` 和 `XinlutongDeviceCoordinator` 实现 `DeviceCoordinator`
3. `BrandCommandDispatcher` 持有 `Map<String, DeviceCoordinator>`，按 `getBrand()` 返回值路由
4. 所有命令方法返回类型改为 `CompletableFuture<...>`
5. `isDeviceOnline()` 保持同步，不放进 `DeviceCoordinator`
6. `DeviceService` 和 `DeviceController` 同步改为异步返回

### 3a：新建 DeviceCoordinator 接口

文件：`device-access-api/src/main/java/com/smartparking/deviceaccess/api/DeviceCoordinator.java`

```java
public interface DeviceCoordinator {
    String getBrand(); // 返回 "ZHENSHI" 或 "XINLUTONG"
    
    CompletableFuture<CommandResultDTO> syncTime(String deviceId);
    CompletableFuture<CommandResultDTO> openGate(String deviceId);
    CompletableFuture<CommandResultDTO> closeGate(String deviceId);
    CompletableFuture<PeripheralControlResult> controlPeripheral(String deviceId, PeripheralControlRequest req);
    CompletableFuture<DisplayResult> displayText(String deviceId, String content, DisplayDirection direction);
    CompletableFuture<DisplayResult> saveDisplay(String deviceId, String content, DisplayDirection direction);
    CompletableFuture<DisplayResult> configDisplay(String deviceId, DisplayConfigRequest req);
    CompletableFuture<VoiceControlResult> controlVoice(String deviceId, VoiceControlRequest req);
    CompletableFuture<DisplayResult> displayTextEnhanced(String deviceId, String content, DisplayDirection direction, 
                                                           com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol.FontType font, 
                                                           int[] color, Integer voiceId, String voiceVariable);
}
```

### 3b：修改 ZhenshiDeviceCoordinator

- 实现 `DeviceCoordinator`
- 添加 `getBrand()` 返回 `"ZHENSHI"`
- 所有命令方法返回类型改为 `CompletableFuture<...>`
- 删除所有 `future.get()`，改为 `.thenApply()` / `.exceptionally()` 链式调用
- `isDeviceOnline()` 保持同步，不实现接口

### 3c：修改 XinlutongDeviceCoordinator

- 实现 `DeviceCoordinator`
- 添加 `getBrand()` 返回 `"XINLUTONG"`
- 所有命令方法返回类型改为 `CompletableFuture<...>`
- 删除所有 `future.get()`，改为 `.thenApply()` / `.exceptionally()` 链式调用
- `isDeviceOnline()` 保持同步，不实现接口

### 3d：修改 BrandCommandDispatcher

- 删除对 `ZhenshiMessageHandler` 和 `XinlutongMessageHandler` 的直接依赖
- 改为注入 `List<DeviceCoordinator>`，构建 `Map<String, DeviceCoordinator>`：
```java
private final Map<String, DeviceCoordinator> coordinators;

public BrandCommandDispatcher(List<DeviceCoordinator> coordinatorList) {
    this.coordinators = coordinatorList.stream()
        .collect(Collectors.toMap(DeviceCoordinator::getBrand, Function.identity()));
}
```
- 所有 `switch` 改为 `coordinators.get(product.getBrand()).xxx(...)`
- 注意：`product.getBrand()` 可能是 `"臻识"`、`"ZHENSHI"`、`"信路通"`，确保 `getBrand()` 返回值匹配

### 3e：修改 DeviceService

**全链路调用方清单**：
- `syncTime(String)` → `CompletableFuture<CommandResultDTO>`
- `openGate(String)` → `CompletableFuture<CommandResultDTO>`
- `closeGate(String)` → `CompletableFuture<CommandResultDTO>`
- `controlPeripheral(String, PeripheralControlRequest)` → `CompletableFuture<PeripheralControlResult>`
- `displayText(String, DisplayTextRequest)` → `CompletableFuture<DisplayResult>`
- `saveDisplay(String, DisplaySaveRequest)` → `CompletableFuture<DisplayResult>`
- `configDisplay(String, DisplayConfigRequest)` → `CompletableFuture<DisplayResult>`
- `controlVoice(String, VoiceControlRequest)` → `CompletableFuture<VoiceControlResult>`
- `displayTextEnhanced(String, DisplayTextRequest)` → `CompletableFuture<DisplayResult>`
- `getStatus(String)` 中的 `isDeviceOnline()` 保持同步，通过 `BrandCommandDispatcher` 直接调用 Handler

### 3f：修改 DeviceController

- 所有命令接口返回类型改为 `CompletableFuture<Result<...>>`
- Spring 会自动处理 `CompletableFuture` 的异步返回
- 示例：
```java
@PostMapping("/{deviceId}/time/sync")
public CompletableFuture<Result<CommandResultDTO>> syncTime(@PathVariable String deviceId) {
    log.info("API: syncTime deviceId={}", deviceId);
    return deviceService.syncTime(deviceId)
        .thenApply(result -> result.getSuccess() ? Result.ok(result) : Result.fail(500, result.getMessage()));
}
```

---

## 任务4：MQTT 会话持久化

文件：`device-access-mqtt/src/main/java/com/smartparking/deviceaccess/mqtt/MqttGatewayImpl.java`

### 4.1 固定 clientId

**问题**：`UUID.randomUUID()` 导致每次重启 clientId 不同，配合 `cleanSession=true` 会丢失未消费消息。

**改法**：
- 删除 `UUID.randomUUID().toString().substring(0, 8)`
- 改为稳定实例标识：
```java
String clientId = properties.getClientId() + "_" + getInstanceId();

private String getInstanceId() {
    String configured = properties.getInstanceId();
    if (configured != null && !configured.isBlank()) return configured;
    
    String env = System.getenv("HOSTNAME"); // Docker 默认有
    if (env != null && !env.isBlank()) return env;
    
    try {
        return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
        return "default-" + System.currentTimeMillis();
    }
}
```
- 在 `MqttProperties` 中新增 `instanceId` 字段（可选配置）

### 4.2 持久化会话

**改法**：
- `options.setCleanSession(true)` → `options.setCleanSession(false)`
- `new MemoryPersistence()` → `new MqttDefaultFilePersistence(System.getProperty("java.io.tmpdir") + "/mqtt-persistence")`
- Windows 兼容，`java.io.tmpdir` 自动适配

---

## 任务5：信路通 JSON 构造规范化

文件：`device-access-adapter/src/main/java/com/smartparking/deviceaccess/adapter/xinlutong/XinlutongMessageHandler.java`

### 5.1 修改 sendCommand()

**问题**：`StringBuilder` 拼 JSON 容易出错，且 `escapeJson()` 方法增加维护成本。

**改法**：
- 删除 `StringBuilder` 拼 JSON 方式
- 删除 `escapeJson()` 方法
- 用 `LinkedHashMap` 构造数据（保证字段顺序，信路通设备可能对顺序敏感），通过 `objectMapper.writeValueAsString(map)` 生成 JSON
- **关键**：`data` 字段是**字符串类型**（内含转义后的 JSON），不是嵌套对象。构造时：
```java
// data 字段的值必须是 String 类型（JSON 字符串），不是 Map
// 构造时先用 objectMapper.writeValueAsString() 把内层对象转成字符串，再 put 到外层 Map
String dataJson = data != null && !data.isEmpty() ? data : "";

Map<String, Object> map = new LinkedHashMap<>();
map.put("command", command);
map.put("data", dataJson);  // 注意：这是 String 值，不是 Map
map.put("requestId", requestId);
map.put("sn", deviceSn);
map.put("time", time);
map.put("version", "1.0.1");

String jsonStr = objectMapper.writeValueAsString(map);
```

### 5.2 修改 sendSerialData()

**改法**：
- 同样删除 `StringBuilder` 和 `escapeJson()`
- `serialData` 内层先构造 `Map`，转成 JSON 字符串，再作为外层 `data` 字段的值：
```java
// 1. 构造 serialData 内层对象
Map<String, Object> serialDataItem = new LinkedHashMap<>();
serialDataItem.put("channel", serialChannel);
serialDataItem.put("data", base64Data);
serialDataItem.put("len", rawData.length);

Map<String, Object> serialDataInner = new LinkedHashMap<>();
serialDataInner.put("serialData", List.of(serialDataItem));

// 2. 将内层对象转为 JSON 字符串（作为外层 data 字段的值）
String dataJson = objectMapper.writeValueAsString(serialDataInner);

// 3. 构造外层 Map
Map<String, Object> map = new LinkedHashMap<>();
map.put("command", "SerialData");
map.put("data", dataJson);  // 注意：这是 String 值
map.put("requestId", requestId);
map.put("sn", deviceSn);
map.put("time", time);
map.put("version", "1.0.1");

String jsonStr = objectMapper.writeValueAsString(map);
```
- 保持生成的 JSON 字段名和顺序与原来完全一致

---

## 输出格式

每完成一个任务，告诉我：
- 修改了哪些文件
- 关键代码 diff（只显示修改部分）
- 是否修改了 `pom.xml`（引入新依赖）
- 是否有架构变更（Core Capability 需标注）
