import request from '@/utils/request'
import type { PageResult } from './parking-manage'

// ============ 管理员账号（AD-01，对齐后端 AdminAccountVO） ============

/** 管理员级别 */
export const AccountLevel = {
  PLATFORM: 1,
  TENANT: 2,
  BOOTH: 3,
} as const

/** 账号状态：1正常 0禁用 2锁定（以后端 service 常量为准） */
export const AccountStatus = {
  DISABLED: 0,
  NORMAL: 1,
  LOCKED: 2,
} as const

export interface AdminAccount {
  /** 后端序列化为字符串，防止 JS Number 精度丢失 */
  id: string
  tenantId: number | null
  companyId: number | null
  lotId: number | null
  username: string
  realName: string
  phone: string | null
  email: string | null
  level: number
  status: number
  loginFailCount: number
  lockUntil: string | null
  lastLoginTime: string | null
  mustChangePassword: number
  allowFeeReduction: number
  parkingLotIds: number[] | null
  roleIds: number[] | null
  createdAt: string
  updatedAt: string
}

export interface AdminAccountPageQuery {
  page: number
  size: number
  keyword?: string
  status?: number
}

export interface AdminAccountCreateCmd {
  username: string
  /** 留空则由后端随机生成 8 位初始密码 */
  password?: string
  realName: string
  phone?: string
  email?: string
  /** 2=租户管理员 3=岗亭管理员 */
  level: number
  /** level=2 必填 */
  tenantId?: number | null
  /** level=2 必填，level=3 可选 */
  companyId?: number | null
  lotId?: number | null
  roleIds?: number[]
  parkingLotIds?: number[]
  mustChangePassword?: number
  allowFeeReduction?: number
}

export interface AdminAccountUpdateCmd {
  realName: string
  phone?: string
  email?: string
  level: number
  companyId?: number | null
  lotId?: number | null
  status: number
  roleIds?: number[]
  parkingLotIds?: number[]
  mustChangePassword?: number
  allowFeeReduction?: number
}

/** 分页查询管理员账号 */
export function getAdminAccounts(params: AdminAccountPageQuery): Promise<PageResult<AdminAccount>> {
  return request.get('/v1/admin-accounts', params)
}

/** 获取账号详情（含 roleIds / parkingLotIds） */
export function getAdminAccount(id: string): Promise<AdminAccount> {
  return request.get(`/v1/admin-accounts/${id}`)
}

/** 创建管理员账号 */
export function createAdminAccount(data: AdminAccountCreateCmd): Promise<AdminAccount> {
  return request.post('/v1/admin-accounts', data)
}

/** 更新管理员账号 */
export function updateAdminAccount(id: string, data: AdminAccountUpdateCmd): Promise<AdminAccount> {
  return request.put(`/v1/admin-accounts/${id}`, data)
}

/** 删除管理员账号 */
export function deleteAdminAccount(id: string): Promise<void> {
  return request.delete(`/v1/admin-accounts/${id}`)
}

/** 重置密码，返回明文新密码（仅本次可见） */
export function resetPassword(id: string): Promise<{ plainPassword: string }> {
  return request.post(`/v1/admin-accounts/${id}/reset-password`)
}

// ============ 自定义角色（账号表单绑定角色用） ============

export interface CustomRoleVO {
  id: number
  tenantId: number | null
  roleName: string
  roleCode: string
  description?: string
}

export function getCustomRoles(params: { page: number; size: number; keyword?: string }): Promise<PageResult<CustomRoleVO>> {
  return request.get('/v1/custom-roles', params)
}

// ============ 公司（租户管理员绑定归属公司用） ============

export interface CompanyOptionVO {
  id: number
  tenantId: number | null
  name: string
  level?: number
}

/** 注意：后端公司分页接口参数为 current/size */
export function getCompanies(params: { current: number; size: number }): Promise<PageResult<CompanyOptionVO>> {
  return request.get('/v1/companies', params)
}
