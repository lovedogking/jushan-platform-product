import request from '@/utils/request'

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
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/**
 * 查询在场车辆。
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
