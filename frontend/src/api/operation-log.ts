import request from '@/utils/request'

export interface GateOperationLogVO {
  id: number
  tenantId: number
  parkingLotId: number
  laneId?: number
  laneName?: string
  operationType: string
  reason?: string
  plateNumber?: string
  direction?: string
  operatorId?: number
  operatorName?: string
  feeCents?: number
  entryImage?: string
  exitImage?: string
  remark?: string
  operationTime: string
}

export function getOperationLogs(params: {
  current: number
  size: number
  parkingLotId?: number
  operationType?: string
  plateNumber?: string
}) {
  const p = params as Record<string, any>
  return request.get<any, { records: GateOperationLogVO[]; total: number }>(
    '/v1/booth/operation-logs',
    { params: p },
  )
}
