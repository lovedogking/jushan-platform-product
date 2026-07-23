import request from '@/utils/request'
import type { ParkingSessionVO } from './monitor-types'

/** 在场车辆 */
export interface PresentVehicle {
  plateNumber: string
  entryTime: string
  durationMinutes: number
  vehicleType: string
  isMonthlyPass: boolean
  isFixedSpace: boolean
  parkingRecordId: number
}

/** 历史通行记录 */
export interface VehicleHistoryRecord {
  plateNumber: string
  entryTime: string
  exitTime: string | null
  feeAmount: number | null
  paymentStatus: string | null
  laneName: string
  /** 入场抓拍图 URL（人工放行时为开闸抓拍图） */
  entryImage?: string | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/**
 * 查询通行记录（支持车牌号、入场触发方式、状态筛选，复用 OP-04 / GB-06）。
 * GET /api/v1/parking-sessions
 */
export function getParkingSessions(params: {
  current?: number
  size?: number
  parkingLotId?: number
  plateNumber?: string
  status?: string
}): Promise<PageResult<ParkingSessionVO>> {
  return request.get<PageResult<ParkingSessionVO>>('/v1/parking-sessions', params)
}

/**
 * 查询在场车辆（旧接口，保留兼容）。
 * GET /api/v1/booth/vehicles/present
 */
export function getPresentVehicles(params: {
  parkingLotId: number
  sortBy?: 'entryTime' | 'duration'
  sortDir?: 'asc' | 'desc'
  page?: number
  size?: number
}): Promise<PageResult<PresentVehicle>> {
  return request.get<PageResult<PresentVehicle>>('/v1/booth/vehicles/present', params)
}

/**
 * 查询历史通行记录。
 * GET /api/v1/booth/vehicles/history
 */
export function getVehicleHistory(params: {
  parkingLotId: number
  plateNumber?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}): Promise<PageResult<VehicleHistoryRecord>> {
  return request.get<PageResult<VehicleHistoryRecord>>('/v1/booth/vehicles/history', params)
}

/**
 * 查询通行记录（支持车牌号筛选，复用 OP-04 / GB-06）。
 * GET /api/v1/booth/vehicles/present
 */
export function getBoothVehicles(plateNumber?: string): Promise<PresentVehicle[]> {
  return request.get<PresentVehicle[]>('/v1/booth/vehicles/present', { plateNumber })
}
