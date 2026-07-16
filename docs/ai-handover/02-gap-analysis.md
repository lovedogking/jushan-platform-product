# Gap 分析报告 — P0-04 风险降级记录

## 结论：P0-04 风险已降级为技术债务

**评估日期**：2026-07-16
**评估人**：Claude Code Phase 0 Day 1

### 风险回顾

P0-04（来自 Gap 分析报告）曾标记为 P0 风险：
> **双包命名空间配置依赖隐患**：@SpringBootApplication 扫描到两个包下的同名 Entity，MyBatis-Plus 可能行为异常。

### 分析结果

`@SpringBootApplication(scanBasePackages = "com.jushan")` 同时覆盖：

| 包路径 | 文件数 |
|--------|--------|
| `com.jushan.platform.*` | ~186 （旧模块，`platform.modules.*` 分包） |
| `com.jushan.system.*` | ~200 （新模块，平面分包） |

#### 实体类（Entity）

- `com.jushan.platform.modules.park.entity.ParkingLot`
- `com.jushan.system.entity.ParkingLot`

问题：确实存在同名实体类，但 Java FQCN 不同，且 Entity 类不带 `@Component` 注解，不参与 Spring Bean 注册，**没有 BeanName 冲突**。
MyBatis-Plus 通过 Mapper 接口的泛型推导关联表，每个 Mapper 只关联自己包下的 Entity，**不存在歧义**。

#### Mapper 接口

- `com.jushan.platform.modules.**.mapper.ParkingLotMapper`（旧 Mapper）
- `com.jushan.system.mapper.ParkingLotMapper`（新 Mapper）

问题：同名 Mapper 接口。但项目已有 `MyBatisConfig.java` 显式配置两个 `MapperScannerConfigurer`，为旧包的 Mapper 添加了 `"modules"` 前缀：

```java
// 旧包 Mapper 的 Bean 名带 "modules" 前缀
configurer.setNameGenerator(new AnnotationBeanNameGenerator() {
    @Override
    protected String buildDefaultBeanName(BeanDefinition definition) {
        return "modules" + super.buildDefaultBeanName(definition);
    }
});
```

**冲突已在此配置中被消除。**

#### Service / Controller

无同名类。所有 ServiceImpl 和 Controller 在新旧包中名称全异。

### 最终判定

| 风险 | 原等级 | 现等级 | 理由 |
|------|--------|--------|------|
| P0-04 双包冲突 | P0 | **技术债务** | Mapper 名冲突已有明确配置处理，Entity 参数化安全 |

### 后续建议

虽然当前无运行时风险，但建议在合适时机（如模块重构 Sprint）将旧 `com.jushan.platform.modules.*` 迁移到 `com.jushan.system.*`，彻底消除双包维护成本。迁移不影响生产可用性，**不阻塞上线**。
