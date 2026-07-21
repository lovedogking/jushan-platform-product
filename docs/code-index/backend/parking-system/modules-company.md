# 模块：company（公司/集团档案）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/company/`
> **所属**：`parking-system` · `com.jushan.platform.modules.company`
> **职责**：租户下的公司/集团档案 CRUD、树形组织查看。
> **最近更新**：2026-07-21

---

## 一、接口入口

### SysCompanyController  `controller/SysCompanyController.java`
- **基础路径**：`/api/v1/companies` ｜ **权限**：`company:*`

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `company:create` | 新增 | `CompanyCreateCmd` | `R<CompanyVO>` |
| update | PUT | `/{id}` | `company:update` | 编辑 | `id, CompanyUpdateCmd` | `R<CompanyVO>` |
| delete | DELETE | `/{id}` | `company:delete` | 软删除 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `company:view` | 详情 | `id` | `R<CompanyVO>` |
| tree | GET | `/tree` | `company:view` | 树形结构 | — | `R<List<CompanyTreeVO>>` |
| page | GET | `/` | `company:view` | 分页 | `name,level,parentId,current,size` | `R<IPage<CompanyVO>>` |

---

## 二、Service（接口 + impl）

### SysCompanyService  `service/SysCompanyService.java`
继承 `IService<SysCompany>`。

| 方法 | 签名 | 功能 |
|---|---|---|
| create / updateCompany / deleteCompany | `CompanyVO create(CompanyCreateCmd)` / `updateCompany(Long, CompanyUpdateCmd)` / `void deleteCompany(Long)` | 增改删 |
| detail / pageList | `CompanyVO detail(Long)` / `IPage<CompanyVO> pageList(IPage, String name, Integer level, Long parentId)` | 查询 |
| tree | `List<CompanyTreeVO> tree()` | 租户公司树 |

---

## 三、领域对象

| 类型 | 类名 | 作用 |
|---|---|---|
| Entity | SysCompany | 公司/集团档案（多级 parentId） |
| DTO | CompanyCreateCmd / CompanyUpdateCmd | 创建/编辑 |
| VO | CompanyVO / CompanyTreeVO | 视图/树形 |

---

## 四、Mapper

| 类名 | 关键方法 |
|---|---|
| SysCompanyMapper | `selectTreeByTenantId(tenantId)`、`countAdminAccountByCompanyId(companyId)` |
