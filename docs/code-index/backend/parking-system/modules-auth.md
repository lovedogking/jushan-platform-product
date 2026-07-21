# 模块：auth（登录鉴权）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/auth/`
> **所属**：`parking-system` · `com.jushan.platform.modules.auth`
> **职责**：后台/岗亭端的登录、Token 刷新、会话查询、退出、密码修改。
> **最近更新**：2026-07-21

---

## 一、接口入口

### AuthController  `controller/AuthController.java`
- **基础路径**：`/api/v1/auth` ｜ **权限**：无（公开接口）

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| login | POST | `/login` | 登录（含 Redis 限流、审计） | `LoginRequest` | `R<LoginResult>` |
| refresh | POST | `/refresh` | 刷新 Token | `LoginResult` | `R<LoginResult>` |
| userinfo | GET | `/userinfo` | 当前用户信息（含角色/权限码） | — | `R<LoginResult.UserInfo>` |
| session | GET | `/session` | Sa-Token 会话信息 | — | `R<SessionInfo>` |
| logout | POST | `/logout` | 退出登录 | — | `R<Void>` |

### ChangePasswordController  `controller/ChangePasswordController.java`
- **基础路径**：`/api/v1/auth`（与前一个共享） ｜ **权限**：需登录

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| changePassword | POST | `/change-password` | 修改密码（验证旧密码） | `ChangePasswordRequest` | `R<Void>` |

---

## 二、领域对象

| 类型 | 类名 | 作用 |
|---|---|---|
| DTO | LoginRequest | 登录请求（username + password） |
| VO | LoginResult | 登录结果（token + 用户信息） |

---

## 三、跨模块依赖

- `AuthController.login` → `account/SysAdminAccountService.onLoginSuccess/onLoginFail` 记录成败审计。
- 登录后权限码来源：`account/SysPermissionProvider` + `SysRolePermissionMapper.selectPermissionCodesByRoleIds`。
