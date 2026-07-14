import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface AdminAccountVO {
  id: number
  tenantId?: number
  companyId?: number
  lotId?: number
  username: string
  realName?: string
  phone?: string
  email?: string
  level: number
  status: number
  lastLoginTime?: string
  createdAt?: string
  roleIds?: number[]
  roleNames?: string[]
}

export interface AdminAccountCreateCmd {
  username: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  level: number
  companyId?: number
  lotId?: number
  status: number
  roleIds?: number[]
}

export interface AdminAccountUpdateCmd {
  id: number
  realName?: string
  phone?: string
  email?: string
  level: number
  companyId?: number
  lotId?: number
  status: number
  roleIds?: number[]
}

export interface CustomRoleVO {
  id: number
  tenantId: number
  roleName: string
  roleCode: string
  description?: string
  createdAt?: string
}

export interface CustomRoleCreateCmd {
  roleName: string
  roleCode: string
  description?: string
}

export interface CustomRoleUpdateCmd extends CustomRoleCreateCmd {
  id: number
}

export interface RolePermissionVO {
  permissionCode: string
  permissionType: string
  dataScope?: string
}

export interface ResetPasswordVO {
  plainPassword: string
}

/** 创建账号 */
export function createAdminAccount(data: AdminAccountCreateCmd) {
  return request.post('/v1/admin-accounts', data)
}

/** 编辑账号 */
export function updateAdminAccount(id: number, data: AdminAccountUpdateCmd) {
  return request.put(`/v1/admin-accounts/${id}`, data)
}

/** 删除账号 */
export function deleteAdminAccount(id: number) {
  return request.delete(`/v1/admin-accounts/${id}`)
}

/** 账号详情 */
export function getAdminAccountDetail(id: number) {
  return request.get<AdminAccountVO>(`/v1/admin-accounts/${id}`)
}

/** 账号分页列表 */
export function getAdminAccountPage(params: { current?: number; size?: number; keyword?: string; status?: number }) {
  return request.get<PageResult<AdminAccountVO>>('/v1/admin-accounts', params)
}

/** 重置密码 */
export function resetAdminPassword(id: number) {
  return request.post<ResetPasswordVO>(`/v1/admin-accounts/${id}/reset-password`)
}

/** 创建角色 */
export function createCustomRole(data: CustomRoleCreateCmd) {
  return request.post('/v1/custom-roles', data)
}

/** 编辑角色 */
export function updateCustomRole(id: number, data: CustomRoleUpdateCmd) {
  return request.put(`/v1/custom-roles/${id}`, data)
}

/** 删除角色 */
export function deleteCustomRole(id: number) {
  return request.delete(`/v1/custom-roles/${id}`)
}

/** 角色列表 */
export function getCustomRoleList(params?: { current?: number; size?: number; keyword?: string }) {
  return request.get<PageResult<CustomRoleVO>>('/v1/custom-roles', params)
}

/** 角色权限矩阵 */
export function getRolePermissions(id: number) {
  return request.get<RolePermissionVO[]>(`/v1/custom-roles/${id}/permissions`)
}

/** 保存角色权限矩阵 */
export function saveRolePermissions(id: number, permissions: RolePermissionVO[]) {
  return request.put(`/v1/custom-roles/${id}/permissions`, { permissions })
}
