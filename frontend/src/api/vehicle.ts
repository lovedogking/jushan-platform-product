import request from '@/utils/request'

// ============ 车辆主档 ============

export interface VehicleVO {
  id: number
  plateNumber: string
  plateColor?: string
  vehicleType: string
  ownerName?: string
  ownerPhone?: string
  departmentId?: number
  parkingLotId: number
  validStartDate?: string
  validEndDate?: string
  prepaidBalance?: number
  feeRuleId?: number
  remark?: string
  status: string
  multiPlates?: string[]
  laneIds?: number[]
  laneNames?: string[]
  createdAt?: string
  updatedAt?: string
}

export interface VehiclePageQuery {
  page: number
  size: number
  plateNumber?: string
  vehicleType?: string
  departmentId?: number
  parkingLotId?: number
  status?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export function getVehicles(params: VehiclePageQuery) {
  return request.get<PageResult<VehicleVO>>('/v1/vehicles', params)
}

export function getVehicleDetail(id: number): Promise<VehicleVO> {
  return request.get<VehicleVO>(`/v1/vehicles/${id}`)
}

export interface VehicleCreateCmd {
  plateNumber: string
  plateColor?: string
  vehicleType: string
  ownerName?: string
  ownerPhone?: string
  departmentId?: number
  parkingLotId: number
  validStartDate: string
  validEndDate?: string
  prepaidBalance?: number
  feeRuleId?: number
  remark?: string
  laneIds?: number[]
}

export function createVehicle(data: VehicleCreateCmd): Promise<VehicleVO> {
  return request.post<VehicleVO>('/v1/vehicles', data)
}

export interface VehicleUpdateCmd {
  plateNumber?: string
  plateColor?: string
  vehicleType?: string
  ownerName?: string
  ownerPhone?: string
  departmentId?: number
  parkingLotId?: number
  validStartDate?: string
  validEndDate?: string
  prepaidBalance?: number
  feeRuleId?: number
  remark?: string
  status?: string
  laneIds?: number[]
}

export function updateVehicle(id: number, data: VehicleUpdateCmd): Promise<VehicleVO> {
  return request.put<VehicleVO>(`/v1/vehicles/${id}`, data)
}

export function deleteVehicle(id: number): Promise<void> {
  return request.delete<void>(`/v1/vehicles/${id}`)
}

/** 按车牌号查询车辆信息 */
export function queryVehicle(plateNumber: string): Promise<VehicleVO | null> {
  return request.get(`/v1/vehicles/query`, { params: { plateNumber } })
}
