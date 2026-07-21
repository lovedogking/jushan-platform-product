<!--
  模块索引模板 —— 新建模块索引文件时复制本文件并按注释填写。
  颗粒度约定见 AGENTS.md 第三节；删除所有 <!-- --> 注释后提交。
-->
# 模块：<模块名>（<中文职责一句话>）

> **包路径**：`<相对仓库根的源码目录>`
> **所属**：`parking-system` / `device-access` / `frontend` …
> **职责**：<一到两句话概括该模块负责什么>
> **最近更新**：YYYY-MM-DD

<!-- 若模块内有需要先了解的约定/依赖/状态（如"冻结""二期候选"），写在这里 -->
**说明**：

---

## 一、接口入口（Controller）

<!-- 每个 Controller 一个三级标题；逐方法列接口清单 -->

### <XxxController>  `<相对路径>.java`

- **基础路径**：`/api/v1/xxx` ｜ **权限前缀**：`xxx:*` ｜ **状态**：正常 / 冻结
- **功能**：<该控制器做什么>

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `xxx:write` | 创建 | `XxxCreateCmd` | `R<XxxVO>` |
| detail | GET | `/{id}` | `xxx:read` | 详情 | `id` | `R<XxxVO>` |

---

## 二、业务服务（Service）

<!-- 两种写法都要标注：① 接口+impl；② 继承 ServiceImpl 的具体类 -->

### <XxxService>

- **类型**：接口 + 实现 / 具体类（继承 `ServiceImpl`）
- **路径**：接口 `<...>/service/XxxService.java`；实现 `<...>/service/impl/XxxServiceImpl.java`
- **功能**：<该服务承担的业务>

| 方法 | 签名 | 功能 |
|---|---|---|
| create | `XxxVO create(XxxCreateCmd cmd)` | 创建 |

---

## 三、领域对象（Entity / DTO / VO）

### Entity

| 类名 | 路径 | 作用 | 关键字段 / 常量 |
|---|---|---|---|
| Xxx | `.../entity/Xxx.java` | 数据表实体 | id, status, … |

### DTO（请求命令）

| 类名 | 路径 | 作用 |
|---|---|---|
| XxxCreateCmd | `.../dto/XxxCreateCmd.java` | 创建请求 |

### VO（响应对象）

| 类名 | 路径 | 作用 |
|---|---|---|
| XxxVO | `.../vo/XxxVO.java` | 详情/列表响应 |

---

## 四、数据层（Mapper）

| 类名 | 路径 | 关键自定义查询 |
|---|---|---|
| XxxMapper | `.../mapper/XxxMapper.java` | `selectByLotId(...)` |

---

## 五、跨模块依赖与备注（可选）

<!-- 该模块调用/被调用的其它模块、外部服务、注意事项 -->
