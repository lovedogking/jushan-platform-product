import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 通行记录 VO */
export interface ParkingRecordAdminVO {
  id: number
  plateNumber: string
  parkingLotId: number
  parkingLotName?: string
  entryLaneId?: number
  entryLaneName?: string
  exitLaneName?: string
  entryTime: string
  exitTime?: string
  parkingDurationMinutes?: number
  status: string
  statusLabel: string
  feeAmount?: number
  paidAmount?: number
  payChannel?: string
  payChannelLabel?: string
  operatorName?: string
  releaseReason?: string
  createdAt: string
  updatedAt?: string
}

/** 状态映射 */
export const RECORD_STATUS_MAP: Record<string, string> = {
  PARKING: '在场',
  COMPLETED: '已离场',
  CANCELLED: '已作废',
}

/**
 * 分页查询通行记录
 */
export function getParkingRecordPage(params: {
  page?: number
  size?: number
  plateNumber?: string
  parkingLotId?: number
  laneId?: number
  startTime?: string
  endTime?: string
  status?: string
}) {
  return request.get<PageResult<ParkingRecordAdminVO>>('/v1/admin/parking-records', params)
}

/**
 * 导出通行记录
 */
export function exportParkingRecords(params: {
  plateNumber?: string
  parkingLotId?: number
  laneId?: number
  startTime?: string
  endTime?: string
  status?: string
}) {
  return request.get<Blob>('/v1/admin/parking-records/export', params, {
    responseType: 'blob',
  } as any)
}
