import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface MonthlyPassVO {
  id: number
  plateNumber: string
  plateColor?: string
  parkingLotId: number
  parkingLotName?: string
  vehicleType: string
  validStartDate?: string
  validEndDate?: string
  status: string
  reviewStatus?: string
  reviewRemark?: string
  payMethod?: string
  paidAmountCents?: number
  ownerName?: string
  ownerPhone?: string
  remark?: string
  createdAt?: string
}

export interface MonthlyPassCreateRequest {
  plateNumber: string
  plateColor?: string
  parkingLotId: number
  validStartDate: string
  validEndDate: string
  amountCents?: number
  ownerName?: string
  ownerPhone?: string
  remark?: string
}

export interface MonthlyPassRenewRequest {
  renewalMonths: number
  amountCents: number
  remark?: string
}

export const MONTHLY_STATUS_MAP: Record<string, string> = {
  ACTIVE: '生效中',
  EXPIRED: '已过期',
  DISABLED: '已注销',
}

/** 月卡分页列表 */
export function getMonthlyPassPage(params: {
  page?: number
  size?: number
  plateNumber?: string
  parkingLotId?: number
  status?: string
  validEndFrom?: string
  validEndTo?: string
}) {
  return request.get<PageResult<MonthlyPassVO>>('/v1/monthly-passes', params)
}

/** 到期预警列表 */
export function getExpiringList(params: {
  days?: number
  page?: number
  size?: number
}) {
  return request.get<PageResult<MonthlyPassVO>>('/v1/monthly-passes/expiring', params)
}

/** 登记月卡 */
export function createMonthlyPass(data: MonthlyPassCreateRequest) {
  return request.post<MonthlyPassVO>('/v1/monthly-passes', data)
}

/** 月卡续期 */
export function renewMonthlyPass(id: number, data: MonthlyPassRenewRequest) {
  return request.put<MonthlyPassVO>(`/v1/monthly-passes/${id}/renew`, data)
}

/** 月卡注销 */
export function cancelMonthlyPass(id: number) {
  return request.put<MonthlyPassVO>(`/v1/monthly-passes/${id}/cancel`)
}

/** 待审核月卡列表 */
export function getMonthlyPassPendingList(params: {
  page?: number
  size?: number
  parkingLotId?: number
}) {
  return request.get<PageResult<MonthlyPassVO>>('/v1/admin/monthly-pass-audit/pending', params)
}

/** 通过月卡审核 */
export function approveMonthlyPass(id: number, remark?: string) {
  return request.post<MonthlyPassVO>(`/v1/admin/monthly-pass-audit/${id}/approve`, null, {
    params: remark ? { remark } : undefined,
  })
}

/** 驳回月卡审核 */
export function rejectMonthlyPass(id: number, remark?: string) {
  return request.post<MonthlyPassVO>(`/v1/admin/monthly-pass-audit/${id}/reject`, null, {
    params: remark ? { remark } : undefined,
  })
}
