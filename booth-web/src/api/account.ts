import request from '@/utils/request'

export interface AdminAccount {
  id: number
  username: string
  displayName: string
  phone: string
  tenantId: number | null
  roleIds: number[]
  roleNames: string[]
  parkingLotIds: number[]
  parkingLotNames: string[]
  parentAccountId: number | null
  parentAccountName: string | null
  status: number
  mustChangePassword: number
  permissions: string[]
  createdAt: string
}

export interface AdminAccountPageQuery {
  page: number
  size: number
  keyword?: string
  status?: number
}

export interface AdminAccountCreateCmd {
  username: string
  password: string
  displayName: string
  phone?: string
  tenantId?: number | null
  roleIds: number[]
  parkingLotIds?: number[]
  parentAccountId?: number | null
  permissions?: string[]
}

export interface AdminAccountUpdateCmd {
  displayName?: string
  phone?: string
  roleIds?: number[]
  parkingLotIds?: number[]
  parentAccountId?: number | null
  permissions?: string[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** 分页查询管理员账号 */
export function getAdminAccounts(params: AdminAccountPageQuery): Promise<PageResult<AdminAccount>> {
  return request.get('/v1/admin-accounts', params)
}

/** 获取账号详情 */
export function getAdminAccount(id: number): Promise<AdminAccount> {
  return request.get(`/v1/admin-accounts/${id}`)
}

/** 创建管理员账号 */
export function createAdminAccount(data: AdminAccountCreateCmd): Promise<AdminAccount> {
  return request.post('/v1/admin-accounts', data)
}

/** 更新管理员账号 */
export function updateAdminAccount(id: number, data: AdminAccountUpdateCmd): Promise<AdminAccount> {
  return request.put(`/v1/admin-accounts/${id}`, data)
}

/** 删除管理员账号 */
export function deleteAdminAccount(id: number): Promise<void> {
  return request.delete(`/v1/admin-accounts/${id}`)
}

/** 重置密码 */
export function resetPassword(id: number): Promise<{ password: string }> {
  return request.post(`/v1/admin-accounts/${id}/reset-password`)
}
