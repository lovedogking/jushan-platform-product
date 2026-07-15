import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface VehicleVO {
  id: number
  tenantId?: number
  plateNumber: string
  plateColor?: string
  vehicleType: string
  ownerName?: string
  ownerPhone?: string
  departmentId?: number
  parkingLotId?: number
  validStartDate?: string
  validEndDate?: string
  prepaidBalance?: number
  feeRuleId?: number
  remark?: string
  status: string
  multiPlates?: string[]
  createdAt?: string
  updatedAt?: string
}

export interface VehicleRenewalCmd {
  renewalMonths: number
  payChannel: string
  amountCents: number
}

export interface RenewalOrderVO {
  orderId: number
  orderNo: string
  amountCents: number
  status: string
  vehicleId: number
  renewalMonths: number
  plateNumber: string
  newValidEndDate?: string
}

export interface RenewalPreviewVO {
  vehicleId: number
  plateNumber: string
  vehicleType: string
  currentEndDate?: string
  newEndDate?: string
  renewalMonths: number
}

export const VEHICLE_TYPE_MAP: Record<string, string> = {
  FREE: '免费车',
  MONTHLY: '月租车(固定车)',
  PREPAID: '储值车',
  VIP: '贵宾车',
  SUPER: '超级车牌',
  BLACKLIST: '黑名单',
}

export const VEHICLE_STATUS_MAP: Record<string, string> = {
  ACTIVE: '正常',
  EXPIRED: '已过期',
  DISABLED: '已禁用',
}

export const PAY_CHANNEL_MAP: Record<string, string> = {
  PYUN: 'P云',
  WECHAT: '微信',
  ALIPAY: '支付宝',
  CASH: '现金',
  BALANCE: '余额',
}

/** 车辆分页列表 */
export function getVehiclePage(params: {
  current?: number
  size?: number
  plateNumber?: string
  vehicleType?: string
  departmentId?: number
  parkingLotId?: number
  status?: string
}) {
  return request.get<PageResult<VehicleVO>>('/v1/vehicles', params)
}

/** 车辆详情 */
export function getVehicleDetail(id: number) {
  return request.get<VehicleVO>(`/v1/vehicles/${id}`)
}

/** 续费有效期预览（不落库） */
export function previewRenewal(id: number, renewalMonths: number) {
  return request.get<RenewalPreviewVO>(`/v1/vehicles/${id}/renew-preview`, { renewalMonths })
}

/** 发起月卡续费（创建 MONTH_RENEW 订单） */
export function renewVehicle(id: number, data: VehicleRenewalCmd) {
  return request.post<RenewalOrderVO>(`/v1/vehicles/${id}/renew`, data)
}

/** 续费支付确认（支付回调/人工查询确认后触发生效） */
export function confirmRenewal(orderId: number, paySerial?: string) {
  return request.post<RenewalOrderVO>(`/v1/vehicles/renew/${orderId}/confirm`, undefined, {
    params: paySerial ? { paySerial } : undefined,
  })
}
