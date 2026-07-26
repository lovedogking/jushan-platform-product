import request from './request'
import type { H5PayResultVO } from './types'

/**
 * 发起支付（锁定订单为 PAYING）。
 * POST /api/v1/h5/pay/prepare
 */
export function preparePay(orderId: number): Promise<H5PayResultVO> {
  return request.post<H5PayResultVO>('/v1/h5/pay/prepare', { orderId })
}

/**
 * 查询支付状态。
 * GET /api/v1/h5/pay/query?orderId=XXX
 */
export function queryPayStatus(orderId: number): Promise<H5PayResultVO> {
  return request.get<H5PayResultVO>('/v1/h5/pay/query', { orderId })
}

/**
 * 模拟支付通知（确认支付成功或失败）。
 * POST /api/v1/h5/pay/notify
 */
export function notifyPay(orderId: number, action: 'success' | 'fail'): Promise<void> {
  return request.post('/v1/h5/pay/notify', { orderId, action })
}
