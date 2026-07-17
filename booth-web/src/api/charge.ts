import request from '@/utils/request'
import type { ChargeInfo, GateOpenResult, ParkingSessionVO, FeeRule } from './monitor-types'

/**
 * 查询待收费信息（按车牌查询在场记录）。
 * GET /api/v1/parking-sessions/in/plate/{plateNumber}
 */
export function getChargeInfo(plateNumber: string): Promise<ParkingSessionVO> {
  return request.get<ParkingSessionVO>(`/v1/parking-sessions/in/plate/${encodeURIComponent(plateNumber)}`)
}

/**
 * 提交收费（完成出场记录更新）。
 * POST /api/v1/parking-sessions/exit
 * 注意：paymentMethod/authCode 为前端扩展字段，
 * 后端 ParkingSessionExitCmd 当前未包含这些字段，Spring Boot 会忽略未知 JSON 字段。
 * 后续需扩展后端接口以支持支付方式记录。
 */
export function submitCharge(data: {
  sessionId: number
  exitLaneId: number
  feeAmount: number
  paidAmount: number
  paymentMethod: string
  authCode?: string
  remark?: string
}) {
  return request.post<ParkingSessionVO>('/v1/parking-sessions/exit', {
    sessionId: data.sessionId,
    exitLaneId: data.exitLaneId,
    feeAmount: data.feeAmount,
    paidAmount: data.paidAmount,
    paymentMethod: data.paymentMethod,
    authCode: data.authCode,
    remark: data.remark || '',
  })
}

/**
 * 人工开闸。
 * POST /api/v1/booth/recognition/manual-open-gate
 */
export function manualOpenGate(laneId: number, reason: string): Promise<GateOpenResult> {
  return request.post<GateOpenResult>(
    '/v1/booth/recognition/manual-open-gate',
    undefined,
    { params: { laneId, reason } },
  )
}

/**
 * 批量多通道开闸（Phase 2 D5）。
 * POST /api/v1/booth/recognition/manual-open-gate-batch
 */
export function manualOpenGateBatch(data: {
  deviceIds: number[]
  reason: string
  isCharge?: boolean
  amount?: number
}): Promise<{
  success: { deviceId: number; success: boolean; message: string }[]
  failed: { deviceId: number; success: boolean; message: string }[]
  total: number
  successCount: number
  failedCount: number
}> {
  return request.post('/v1/booth/recognition/manual-open-gate-batch', data)
}

/**
 * 人工关闸。
 * POST /api/v1/booth/recognition/manual-close-gate
 */
export function manualCloseGate(laneId: number, reason: string): Promise<GateOpenResult> {
  return request.post<GateOpenResult>(
    '/v1/booth/recognition/manual-close-gate',
    undefined,
    { params: { laneId, reason } },
  )
}

/**
 * 查询车道当前生效的收费规则。
 * GET /api/v1/fee-rules/current
 */
export function getCurrentFeeRule(lotId: number, zoneId?: number): Promise<FeeRule> {
  return request.get<FeeRule>('/v1/fee-rules/current', { lotId, zoneId })
}

/**
 * 岗亭端临时调整收费规则。
 * PUT /api/v1/fee-rules/{id}
 */
export function updateFeeRule(id: number, data: Partial<FeeRule>): Promise<FeeRule> {
  return request.put<FeeRule>(`/v1/fee-rules/${id}`, data)
}
