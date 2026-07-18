import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 租户视图 */
export interface TenantVO {
  id: number
  name: string
  contactPerson: string
  contactPhone: string
  status: string
  adminUserId: number
  adminUsername: string
  maxParkingLots: number
  maxDevices: number
  maxEmployees: number
  createdAt: string
  updatedAt: string
}

/** 创建租户命令 */
export interface CreateTenantCmd {
  name: string
  contactPerson: string
  contactPhone: string
  maxParkingLots?: number
  maxDevices?: number
  maxEmployees?: number
}

/** 分页查询租户列表 */
export function getTenants(params: { page: number; size: number; status?: string }) {
  return request.get<PageResult<TenantVO>>('/admin/tenants', params)
}

/** 超管直接创建租户 */
export function createTenant(data: CreateTenantCmd) {
  return request.post<TenantVO>('/admin/tenants', data)
}

/** 删除租户 */
export function deleteTenant(id: number) {
  return request.delete(`/admin/tenants/${id}`)
}

/** 查询租户详情 */
export function getTenant(id: number) {
  return request.get<TenantVO>(`/admin/tenants/${id}`)
}

/** 审核/启停租户 */
export function auditTenant(id: number, data: { action: string; reason?: string }) {
  return request.post(`/admin/tenants/${id}/audit`, data)
}
