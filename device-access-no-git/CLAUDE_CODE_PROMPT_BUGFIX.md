# Device Access — Bug 修复入口提示词

## 会话启动指令

1. 先阅读 `CLAUDE.md`（项目宪法）
2. 再阅读 `docs/ARCHITECTURE.md`（架构设计）
3. 再阅读 `docs/ROADMAP.md`（演进路线）
4. 阅读本文档同目录下的 `docs/TASKS_BUGFIX.md`（任务清单）
5. 总结当前项目理解，确认 v0.4 版本边界
6. 按 `docs/TASKS_BUGFIX.md` 中的顺序执行任务，每完成一个编译通过后再进行下一个

---

## 绝对禁止

- 不要修改数据库表结构（DDL）
- 不要修改 HTTP API 的 URL 路径和方法签名（@PostMapping 等）
- 不要引入 Spring WebFlux（保持 Spring MVC）
- 不要修改测试文件（test/ 目录），除非测试编译失败
- 不要删除现有的 @Autowired(required = false) 注入方式
- 不要修改 OlmM1dProtocol 的协议帧构造逻辑
- 不要在 api 层引入 adapter 层的具体类（必须通过 DeviceCoordinator 接口）
- 不要引入新的外部依赖（Caffeine 除外，已在 device-access-adapter/pom.xml 添加）
- 所有修改必须有中文注释说明为什么改

---

## 回滚策略

修改前确保当前代码已 git commit，方便回滚。

---

## 验证要求

每完成一个任务：
1. `mvn clean compile` 编译通过
2. 检查编译警告（特别是 raw type 和 unchecked cast）
3. 不运行测试（测试依赖真实设备）
