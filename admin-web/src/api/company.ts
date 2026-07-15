import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface CompanyVO {
  id: number
  tenantId: number
  parentId: number
  name: string
  code?: string
  level: number
  contactName?: string
  contactPhone?: string
  address?: string
  sortOrder: number
  createdAt?: string
  updatedAt?: string
  children?: CompanyVO[]
}

export interface CompanyCreateCmd {
  parentId: number
  name: string
  code?: string
  level: number
  contactName?: string
  contactPhone?: string
  address?: string
  sortOrder?: number
}

export interface CompanyUpdateCmd extends CompanyCreateCmd {
  id: number
}

/** 新增公司 */
export function createCompany(data: CompanyCreateCmd) {
  return request.post('/admin/companies', data)
}

/** 编辑公司 */
export function updateCompany(id: number, data: CompanyUpdateCmd) {
  return request.put(`/admin/companies/${id}`, data)
}

/** 删除公司 */
export function deleteCompany(id: number) {
  return request.delete(`/admin/companies/${id}`)
}

/** 公司详情 */
export function getCompanyDetail(id: number) {
  return request.get<CompanyVO>(`/admin/companies/${id}`)
}

/** 公司树形列表 */
export function getCompanyTree() {
  return request.get<CompanyVO[]>('/admin/companies/tree')
}

/** 公司分页列表 */
export function getCompanyPage(params: { page?: number; size?: number; name?: string; level?: number; parentId?: number }) {
  return request.get<PageResult<CompanyVO>>('/admin/companies', params)
}

/** 公司列表（兼容旧接口） */
export function getCompanies(params: { page?: number; size?: number; name?: string }) {
  return request.get<PageResult<CompanyVO>>('/admin/companies', { current: params.page, size: params.size, name: params.name })
}
