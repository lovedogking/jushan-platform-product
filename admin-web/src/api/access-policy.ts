import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 进出策略视图 */
export interface AccessPolicyVO {
  id: number
  parkingLotId: number
  policyType: string
  policyKey: string
  policyValue: string
  description: string
  sortOrder: number
  status: string
  createdAt: string
  updatedAt: string
}

/** 策略类型选项 */
export const POLICY_TYPE_OPTIONS = [
  { label: '黑名单', value: 'BLACKLIST' },
  { label: '白名单', value: 'VIP' },
]

/** 策略类型文本 */
export function policyTypeText(type: string) {
  const opt = POLICY_TYPE_OPTIONS.find(o => o.value === type)
  return opt?.label || type
}

/** 策略类型颜色 */
export function policyTypeColor(type: string) {
  return type === 'BLACKLIST' ? 'red' : 'green'
}

/** 状态文本 */
export function statusText(status: string) {
  return status === 'ACTIVE' ? '启用' : '禁用'
}

/** 状态颜色 */
export function statusColor(status: string) {
  return status === 'ACTIVE' ? 'green' : 'red'
}

/** 分页查询进出策略列表 */
export function getAccessPolicies(params: {
  current?: number
  size?: number
  parkingLotId?: number
  policyType?: string
}) {
  return request.get<PageResult<AccessPolicyVO>>('/v1/access-policies', params)
}

/** 查询进出策略详情 */
export function getAccessPolicy(id: number) {
  return request.get<AccessPolicyVO>(`/v1/access-policies/${id}`)
}

/** 创建进出策略 */
export function createAccessPolicy(data: {
  parkingLotId: number
  policyType: string
  policyKey: string
  policyValue: string
  description?: string
  sortOrder?: number
}) {
  return request.post<AccessPolicyVO>('/v1/access-policies', data)
}

/** 更新进出策略 */
export function updateAccessPolicy(id: number, data: Record<string, any>) {
  return request.put<AccessPolicyVO>(`/v1/access-policies/${id}`, data)
}

/** 删除进出策略 */
export function deleteAccessPolicy(id: number) {
  return request.delete<void>(`/v1/access-policies/${id}`)
}
