import request from '@/utils/request'

/** 交接班记录 */
export interface ShiftRecordVO {
  id: number
  parkingLotId: number
  operatorId: number
  operatorName: string
  shiftType: string
  startTime: string
  endTime: string | null
  entryCount: number
  exitCount: number
  feeAmount: number
  cashAmount: number
  onlineAmount: number
  exceptionCount: number
  handoverStatus: string
  handoverTo: number | null
  handoverRemark: string | null
  adjustReason: string | null
  arrearsCount: number
  handoverOrderCount: number
  arrearsOrders?: ArrearsOrderItem[]
  createdAt: string
  updatedAt: string
}

export interface ArrearsOrderItem {
  orderId: number
  plateNumber: string
  feeCents: number
  createdAt: string
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/**
 * 开班。
 * POST /api/v1/shift-records/start
 */
export function startShift(data: {
  parkingLotId: number
  shiftType: string
  operatorName: string
}): Promise<ShiftRecordVO> {
  return request.post<ShiftRecordVO>('/v1/shift-records/start', data)
}

/**
 * 交班。
 * POST /api/v1/shift-records/close
 */
export function closeShift(data: {
  shiftId: number
  handoverTo?: number
  handoverRemark?: string
  confirmedCashAmount?: number
  adjustReason?: string
}): Promise<ShiftRecordVO> {
  return request.post<ShiftRecordVO>('/v1/shift-records/close', data)
}

/**
 * 获取当前班次信息。
 * GET /api/v1/shift-records/current
 */
export function getCurrentShift(): Promise<ShiftRecordVO | null> {
  return request.get<ShiftRecordVO | null>('/v1/shift-records/current')
}

/**
 * 查询交接班详情。
 * GET /api/v1/shift-records/{id}
 */
export function getShiftDetail(id: number): Promise<ShiftRecordVO> {
  return request.get<ShiftRecordVO>(`/v1/shift-records/${id}`)
}

/**
 * 分页查询交接班历史。
 * GET /api/v1/shift-records
 */
export function getShiftHistory(params: {
  current?: number
  size?: number
  parkingLotId?: number
  status?: string
}): Promise<PageResult<ShiftRecordVO>> {
  return request.get<PageResult<ShiftRecordVO>>('/v1/shift-records', params)
}
