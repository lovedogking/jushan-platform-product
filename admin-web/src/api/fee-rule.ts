import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 收费规则视图 */
export interface FeeRuleVO {
  id: number
  tenantId: number
  lotId: number
  zoneId: number | null
  name: string
  billingMode: number
  freeMinutes: number
  unitMinutes: number
  firstPeriodPrice: string
  subsequentPrice: string
  dailyCap: string | null
  nightCap: string | null
  priority: number
  status: number
  effectiveStart: string | null
  effectiveEnd: string | null
  holidayRules: string | null
  version: number
  timeSegments: FeeRuleSegmentVO[] | null
  createdAt: string
  updatedAt: string
}

/** 收费规则时段视图 */
export interface FeeRuleSegmentVO {
  id: number
  feeRuleId: number
  segmentName: string
  startTime: string
  endTime: string
  unitMinutes: number
  unitPrice: string
  capAmount: string | null
  sortOrder: number
}

/** 计费模式选项 */
export const BILLING_MODE_OPTIONS = [
  { label: '按时计费', value: 1 },
  { label: '按次计费', value: 2 },
  { label: '阶梯计费', value: 3 },
  { label: '分时段计费', value: 4 },
]

/** 状态选项 */
export const FEE_RULE_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 2 },
]

/** 计费模式文本 */
export function billingModeText(mode: number) {
  const opt = BILLING_MODE_OPTIONS.find(o => o.value === mode)
  return opt?.label || '未知'
}

/** 计费模式颜色 */
export function billingModeColor(mode: number) {
  const map: Record<number, string> = { 1: 'blue', 2: 'green', 3: 'orange', 4: 'purple' }
  return map[mode] || 'default'
}

/** 分页查询收费规则列表 */
export function getFeeRules(params: {
  current?: number
  size?: number
  lotId?: number
  zoneId?: number
  billingMode?: number
  status?: number
}) {
  return request.get<PageResult<FeeRuleVO>>('/api/v1/fee-rules', params)
}

/** 查询收费规则详情 */
export function getFeeRule(id: number) {
  return request.get<FeeRuleVO>(`/api/v1/fee-rules/${id}`)
}

/** 创建收费规则 */
export function createFeeRule(data: Record<string, any>) {
  return request.post<FeeRuleVO>('/api/v1/fee-rules', data)
}

/** 更新收费规则 */
export function updateFeeRule(id: number, data: Record<string, any>) {
  return request.put<FeeRuleVO>(`/api/v1/fee-rules/${id}`, data)
}

/** 删除收费规则（软删除） */
export function deleteFeeRule(id: number) {
  return request.delete<void>(`/api/v1/fee-rules/${id}`)
}

/** 复制收费规则 */
export function copyFeeRule(id: number) {
  return request.post<FeeRuleVO>(`/api/v1/fee-rules/${id}/copy`)
}

/** 更新收费规则状态 */
export function updateFeeRuleStatus(id: number, status: number) {
  return request.post<void>(`/api/v1/fee-rules/${id}/status`, undefined, { params: { status } })
}
