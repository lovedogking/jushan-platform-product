import request from '@/utils/request'

export interface FeeRuleVO {
  id: number
  lotId: number
  zoneId?: number
  name: string
  description?: string
  billingMode: number
  vehicleType?: string
  plateColor?: string
  freeMinutes: number
  unitMinutes: number
  firstPeriodMinutes?: number
  firstPeriodPrice?: number
  subsequentPrice?: number
  dailyCap?: number
  maxAmount?: number
  nightCap?: number
  crossDayMode?: number
  effectMode?: number
  priority?: number
  status: number
  effectiveStart?: string
  effectiveEnd?: string
  createdAt?: string
  updatedAt?: string
}

export interface FeeRuleCreateCmd {
  lotId: number
  zoneId?: number
  name: string
  description?: string
  billingMode: number
  vehicleType?: string
  plateColor?: string
  freeMinutes?: number
  unitMinutes?: number
  firstPeriodMinutes?: number
  firstPeriodPrice?: number
  subsequentPrice?: number
  dailyCap?: number
  maxAmount?: number
  nightCap?: number
  crossDayMode?: number
  effectMode?: number
  priority?: number
  status?: number
  effectiveStart?: string
  effectiveEnd?: string
}

export type FeeRuleUpdateCmd = Partial<FeeRuleCreateCmd>

export interface FeeRulePageParams {
  page: number
  size: number
  lotId?: number
  zoneId?: number
  billingMode?: number
  status?: number
}

const BASE = '/v1/fee-rules'

export function getFeeRules(params: FeeRulePageParams) {
  const p: Record<string, any> = { ...params }
  if (p.page !== undefined) { p.current = p.page; delete p.page }
  return request.get<any, { records: FeeRuleVO[]; total: number }>(BASE, { params: p })
}

export function getFeeRuleDetail(id: number) {
  return request.get<any, FeeRuleVO>(`${BASE}/${id}`)
}

export function createFeeRule(data: FeeRuleCreateCmd) {
  return request.post<any, FeeRuleVO>(BASE, data)
}

export function updateFeeRule(id: number, data: FeeRuleUpdateCmd) {
  return request.put<any, FeeRuleVO>(`${BASE}/${id}`, data)
}

export function deleteFeeRule(id: number) {
  return request.delete(`${BASE}/${id}`)
}

export function updateFeeRuleStatus(id: number, status: number) {
  return request.post(`${BASE}/${id}/status`, null, { params: { status } })
}
