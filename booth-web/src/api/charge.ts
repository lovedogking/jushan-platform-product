import request from '@/utils/request'
import type { ChargeInfo, GateOpenResult, ParkingSessionVO } from './monitor-types'

/**
 * 查询待收费信息（按车牌查询在场记录）。
 * GET /api/v1/parking-sessions/in/plate/{plateNumber}
 */
export function getChargeInfo(plateNumber: string): Promise<ParkingSessionVO> {
  return request.get<ParkingSessionVO>(`/parking-sessions/in/plate/${encodeURIComponent(plateNumber)}`)
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
  return request.post<ParkingSessionVO>('/parking-sessions/exit', {
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
    '/booth/recognition/manual-open-gate',
    undefined,
    { params: { laneId, reason } },
  )
}
