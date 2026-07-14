import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 收费规则视图 */
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
  // 当前生效版本信息
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
  tenantId: number
  parkingLotId: number
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
  effectiveFrom: string
  effectiveTo: string
  createdBy: number
  createdByName: string
  createdAt: string
}

/** 分页查询收费规则列表 */
export function getBillingRules(params: { page: number; size: number; parkingLotId?: number; status?: string }) {
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
  isDefault?: boolean
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
export function updateBillingRule(id: number, data: Record<string, any>) {
  return request.put<BillingRuleVO>(`/admin/billing-rules/${id}`, data)
}

/** 查询规则版本历史 */
export function getBillingRuleVersions(id: number) {
  return request.get<BillingRuleVersionVO[]>(`/admin/billing-rules/${id}/versions`)
}

/** 切换收费规则 */
export function switchBillingRule(parkingLotId: number, data: {
  targetRuleId: number
  applyToExisting: boolean
  reason: string
}) {
  return request.post(`/admin/billing-rules/parking-lots/${parkingLotId}/switch`, data)
}