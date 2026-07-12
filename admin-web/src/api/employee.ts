import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 员工视图 */
export interface EmployeeVO {
  id: number
  phone: string
  displayName: string
  roleCode: string
  roleName: string
  status: string
  parkingLotIds: number[]
  parkingLotNames: string[]
  createdAt: string
  updatedAt: string
}

/** 分页查询员工列表 */
export function getEmployees(params: { page: number; size: number; status?: string }) {
  return request.get<PageResult<EmployeeVO>>('/admin/employees', { params })
}

/** 查询员工详情 */
export function getEmployee(id: number) {
  return request.get<EmployeeVO>(`/admin/employees/${id}`)
}

/** 创建员工 */
export function createEmployee(data: {
  displayName: string
  phone: string
  password: string
  roleCode: string
  parkingLotIds: number[]
}) {
  return request.post<EmployeeVO>('/admin/employees', data)
}

/** 更新员工 */
export function updateEmployee(id: number, data: {
  displayName: string
  roleCode: string
  parkingLotIds: number[]
}) {
  return request.put<EmployeeVO>(`/admin/employees/${id}`, data)
}

/** 重置密码 */
export function resetEmployeePassword(id: number, newPassword: string) {
  return request.post(`/admin/employees/${id}/reset-password`, { newPassword })
}

/** 启用/禁用员工 */
export function updateEmployeeStatus(id: number, action: string) {
  return request.post(`/admin/employees/${id}/status`, undefined, { params: { action } })
}
