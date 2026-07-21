# 模块：account（管理账号 / 角色 / 权限）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/account/`
> **所属**：`parking-system` · `com.jushan.platform.modules.account`
> **职责**：后台管理员账号 CRUD、登录成败记录、密码重置；自定义角色与角色权限矩阵管理；账号-停车场/角色关联。
> **最近更新**：2026-07-21

**说明**：Service 为纯接口 + `impl/` 实现（**不**继承 `IService`）。Mapper 多含 `*IgnoreTenant` 方法，供登录等无租户上下文场景使用。另有 `config/AccountSecurityConfig`（安全配置）、`security/SysPermissionProvider`（权限码提供者，实现 `PermissionProvider` 接口）。

---

## 一、接口入口（Controller）

### SysAdminAccountController  `controller/SysAdminAccountController.java`
- **基础路径**：`/api/v1/admin-accounts` ｜ **权限**：`account:*`

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `account:create` | 创建账号 | `AdminAccountCreateCmd` | `R<AdminAccountVO>` |
| update | PUT | `/{id}` | `account:update` | 编辑账号 | `id, AdminAccountUpdateCmd` | `R<AdminAccountVO>` |
| delete | DELETE | `/{id}` | `account:delete` | 软删除 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `account:view` | 详情 | `id` | `R<AdminAccountVO>` |
| list | GET | `/` | `account:view` | 分页 | `page,size,keyword,status` | `R<IPage<AdminAccountVO>>` |
| resetPassword | POST | `/{id}/reset-password` | `account:update` | 重置密码 | `id` | `R<ResetPasswordVO>` |

### SysCustomRoleController  `controller/SysCustomRoleController.java`
- **基础路径**：`/api/v1/custom-roles` ｜ **权限**：`role:*`

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `role:create` | 创建角色 | `CustomRoleCreateCmd` | `R<CustomRoleVO>` |
| update | PUT | `/{id}` | `role:update` | 编辑角色 | `id, CustomRoleUpdateCmd` | `R<CustomRoleVO>` |
| detail | GET | `/{id}` | `role:view` | 详情 | `id` | `R<CustomRoleVO>` |
| list | GET | `/` | `role:view` | 分页 | `page,size,keyword` | `R<IPage<CustomRoleVO>>` |
| getPermissions | GET | `/{id}/permissions` | `role:view` | 查权限矩阵 | `id` | `R<List<RolePermissionVO>>` |
| savePermissions | PUT | `/{id}/permissions` | `role:update` | 保存权限矩阵 | `id, RolePermissionSaveCmd` | `R<Void>` |

---

## 二、业务服务（Service，接口 + impl）

### SysAdminAccountService  `service/SysAdminAccountService.java`（+ impl）

| 方法 | 签名 | 功能 |
|---|---|---|
| create / update / delete | `AdminAccountVO create(AdminAccountCreateCmd)` / `update(Long, AdminAccountUpdateCmd)` / `void delete(Long)` | 增改删 |
| getById / list | `AdminAccountVO getById(Long)` / `IPage<AdminAccountVO> list(int, int, String, Integer)` | 详情/分页 |
| resetPassword | `ResetPasswordVO resetPassword(Long)` | 重置密码 |
| onLoginSuccess / onLoginFail | `void onLoginSuccess(String)` / `void onLoginFail(String)` | 登录成败记录 |

### SysCustomRoleService  `service/SysCustomRoleService.java`（+ impl）

| 方法 | 签名 | 功能 |
|---|---|---|
| create / update / getById / list | `CustomRoleVO create(CustomRoleCreateCmd)` / `update(Long, CustomRoleUpdateCmd)` / `getById(Long)` / `IPage<CustomRoleVO> list(int,int,String)` | CRUD/分页 |
| savePermissions / getPermissions | `void savePermissions(Long, RolePermissionSaveCmd)` / `List<RolePermissionVO> getPermissions(Long)` | 权限矩阵 |

---

## 三、领域对象（Entity / DTO / VO）

### Entity（`entity/`）

| 类名 | 作用 |
|---|---|
| SysAdminAccount | 管理员账号（多租户，含 `level` 区分平台/租户） |
| SysAdminAccountParkingLot | 账号与停车场多对多关联 |
| SysAdminAccountRole | 账号与角色多对多关联 |
| SysCustomRole | 自定义角色 |
| SysRolePermission | 角色权限矩阵 |

### DTO（`dto/`）

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| AdminAccountCreateCmd | 账号创建 | AdminAccountUpdateCmd | 账号编辑 |
| CustomRoleCreateCmd | 角色创建 | CustomRoleUpdateCmd | 角色编辑 |
| RolePermissionSaveCmd | 保存权限矩阵 | | |

### VO（`vo/`）

| 类名 | 作用 |
|---|---|
| AdminAccountVO | 管理员账号视图 |
| CustomRoleVO | 自定义角色视图 |
| ResetPasswordVO | 重置密码响应（含明文密码） |
| RolePermissionVO | 角色权限矩阵视图 |

---

## 四、数据层（Mapper，`mapper/`）

| 类名 | 关键自定义查询 |
|---|---|
| SysAdminAccountMapper | `selectByUsernameIgnoreTenant`、`selectByIdIgnoreTenant`、`selectPageList`、`updateByIdIgnoreTenant`、`deleteByIdIgnoreTenant` |
| SysAdminAccountParkingLotMapper | `selectParkingLotIdsByAccountId`、`deleteByAdminAccountId` |
| SysAdminAccountRoleMapper | `selectRoleIdsByAdminAccountId`、`deleteByAdminAccountId`、`selectByRoleIdIgnoreTenant`、`insertIgnoreTenant` |
| SysCustomRoleMapper | `selectByRoleCodeIgnoreTenant`、`selectByIdIgnoreTenant`、`selectPageList`、`updateByIdIgnoreTenant`、`deleteByIdIgnoreTenant` |
| SysRolePermissionMapper | `selectByRoleId`、`selectPermissionCodesByRoleIds`、`deleteByRoleId` |

---

## 五、其它

- `config/AccountSecurityConfig.java` — 账号相关安全配置。
- `security/SysPermissionProvider.java` — 实现 `PermissionProvider` 接口，从 DB 提供权限码（供 `@RequirePermission` 校验）。
- **登录链路**：`AuthController.login` → `SysAdminAccountService.onLoginSuccess/onLoginFail`（记录成败）→ 权限码从 `SysPermissionProvider` / `SysRolePermissionMapper.selectPermissionCodesByRoleIds` 获取。
