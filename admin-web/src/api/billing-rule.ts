import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 收费规则视图（旧 billing_rule 体系） */
export interface BillingRuleVO {
  id: number
  tenantId: number
  parkingLotId: number
  parkingLotName: string
  name: string
  description: string
  ruleType: string
  ruleTypeDesc: string
  status: string
  statusDesc: string
  isDefault: number
  createdBy: number
  createdByName: string
  updatedBy: number
  createdAt: string
  updatedAt: string
  activeVersionId: number
  activeVersion: number
  configSummary: string
  freeMinutes: number
  firstPeriod: number
  firstAmount: number
  unitPeriod: number
  unitAmount: number
  dailyCap: number
  maxAmount: number
}

/** 收费规则版本视图 */
export interface BillingRuleVersionVO {
  id: number
  ruleId: number
  version: number
  isActive: number
  config: string
  configSummary: string
  freeMinutes: number
  firstPeriod: number
  firstAmount: number
  unitPeriod: number
  unitAmount: number
  dailyCap: number
  maxAmount: number
  effectiveFrom: string | null
  effectiveTo: string | null
  createdAt: string
}

/** 计费类型选项 */
export const RULE_TYPE_OPTIONS = [
  { label: '按时计费', value: 'HOURLY' },
  { label: '按次计费', value: 'FIXED' },
  { label: '免费', value: 'NO_FEE' },
]

/** 状态选项 */
export const RULE_STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

/** 分页查询收费规则列表 */
export function getBillingRules(params: {
  page?: number
  size?: number
  parkingLotId?: number
  status?: string
}) {
  return request.get<PageResult<BillingRuleVO>>('/admin/billing-rules', params)
}

/** 查询收费规则详情 */
export function getBillingRule(id: number) {
  return request.get<BillingRuleVO>(`/admin/billing-rules/${id}`)
}

/** 创建收费规则 */
export function createBillingRule(data: {
  parkingLotId: number
  name: string
  description?: string
  ruleType: string
  freeMinutes?: number
  firstPeriod?: number
  firstAmount?: number
  unitPeriod?: number
  unitAmount?: number
  dailyCap?: number
  maxAmount?: number
}) {
  return request.post<BillingRuleVO>('/admin/billing-rules', data)
}

/** 更新收费规则 */
export function updateBillingRule(id: number, data: {
  name?: string
  description?: string
  ruleType?: string
  freeMinutes?: number
  firstPeriod?: number
  firstAmount?: number
  unitPeriod?: number
  unitAmount?: number
  dailyCap?: number
  maxAmount?: number
}) {
  return request.put<BillingRuleVO>(`/admin/billing-rules/${id}`, data)
}

/** 查询版本历史 */
export function getBillingRuleVersions(id: number) {
  return request.get<BillingRuleVersionVO[]>(`/admin/billing-rules/${id}/versions`)
}

/** 切换收费规则 */
export function switchBillingRule(parkingLotId: number, data: {
  targetRuleId: number
  applyToExisting?: boolean
  reason: string
}) {
  return request.post<void>(`/admin/billing-rules/parking-lots/${parkingLotId}/switch`, data)
}
