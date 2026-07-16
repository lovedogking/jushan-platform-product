import request from '@/utils/request'
import type { PageResult } from '@/types'

export interface OrderAdminVO {
  id: number
  orderNo: string
  orderType: string
  orderTypeLabel: string
  plateNumber: string
  parkingLotId: number
  parkingLotName?: string
  amountCents: number
  discountAmount?: number
  pointsDiscount?: number
  payableAmount: number
  paidAmount?: number
  status: string
  statusLabel: string
  payChannel?: string
  payChannelLabel?: string
  payTime?: string
  entryTime?: string
  exitTime?: string
  parkingDurationMinutes?: number
  operatorName?: string
  createdAt: string
  updatedAt?: string
}

export const ORDER_STATUS_MAP: Record<string, string> = {
  PENDING_PAY: '待支付',
  PAYING: '支付中',
  PAID: '已支付',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  PAY_FAILED: '支付失败',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}

export const ORDER_TYPE_MAP: Record<string, string> = {
  PARKING: '临停订单',
  MONTH_RENEW: '月卡续费',
  VISITOR: '访客订单',
  TOP_UP: '充值订单',
}

export const PAY_CHANNEL_MAP: Record<string, string> = {
  PYUN: 'P云',
  WECHAT: '微信',
  ALIPAY: '支付宝',
  CASH: '现金',
  BALANCE: '余额',
}

export function getOrderPage(params: {
  page?: number
  size?: number
  orderNo?: string
  plateNumber?: string
  parkingLotId?: number
  startTime?: string
  endTime?: string
  status?: string
  orderType?: string
}) {
  return request.get<PageResult<OrderAdminVO>>('/v1/admin/orders', params)
}

export function getOrderDetail(id: number) {
  return request.get<OrderAdminVO>(`/v1/admin/orders/${id}`)
}

export function closeOrder(id: number) {
  return request.post<{ orderId: number; status: string }>(`/v1/admin/orders/${id}/close`)
}

export function exportOrders(params: {
  orderNo?: string
  plateNumber?: string
  parkingLotId?: number
  startTime?: string
  endTime?: string
  status?: string
  orderType?: string
}) {
  return request.get<Blob>('/v1/admin/orders/export', params, {
    responseType: 'blob',
  } as any)
}
