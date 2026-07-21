# 模块：department（部门/组织架构）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/department/`
> **所属**：`parking-system` · `com.jushan.platform.modules.department`
> **职责**：租户下的部门 CRUD、树形组织查看。
> **最近更新**：2026-07-21

---

## 一、接口入口

### SysDepartmentController  `controller/SysDepartmentController.java`
- **基础路径**：`/api/v1/departments` ｜ **权限**：`department:*`

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `department:create` | 新增 | `DepartmentCreateCmd` | `R<DepartmentVO>` |
| update | PUT | `/{id}` | `department:update` | 编辑 | `id, DepartmentUpdateCmd` | `R<DepartmentVO>` |
| delete | DELETE | `/{id}` | `department:delete` | 软删除 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `department:view` | 详情 | `id` | `R<DepartmentVO>` |
| tree | GET | `/tree` | `department:view` | 树形结构 | — | `R<List<DepartmentTreeVO>>` |
| page | GET | `/` | `department:view` | 分页 | `name,parkingLotId,parentId,current,size` | `R<IPage<DepartmentVO>>` |

---

## 二、Service（接口 + impl）

### SysDepartmentService  `service/SysDepartmentService.java`
继承 `IService<SysDepartment>`。

| 方法 | 签名 | 功能 |
|---|---|---|
| create / updateDepartment / deleteDepartment | `DepartmentVO create(DepartmentCreateCmd)` / `updateDepartment(Long, DepartmentUpdateCmd)` / `void deleteDepartment(Long)` | 增改删 |
| detail / pageList | `DepartmentVO detail(Long)` / `IPage<DepartmentVO> pageList(IPage, String name, Long parkingLotId, Long parentId)` | 查询 |
| tree | `List<DepartmentTreeVO> tree()` | 租户部门树 |

---

## 三、领域对象

| 类型 | 类名 | 作用 |
|---|---|---|
| Entity | SysDepartment | 部门/组织架构 |
| DTO | DepartmentCreateCmd / DepartmentUpdateCmd | 创建/编辑 |
| VO | DepartmentVO / DepartmentTreeVO | 视图/树形 |

---

## 四、Mapper

| 类名 | 关键方法 |
|---|---|
| SysDepartmentMapper | `countChildren(parentId)`、`selectByParkingLotId(parkingLotId)` |
