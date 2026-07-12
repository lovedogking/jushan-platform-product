import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 停车场视图 */
export interface ParkingLotVO {
  id: number
  tenantId: number
  name: string
  address: string
  contactPhone: string
  totalSpaces: number
  currentVehicles: number
  remainingSpaces: number
  status: string
  paymentMode: string
  imageRetentionDays: number
  dataRetentionDays: number
  freeExitMinutes: number
  manualReleasePolicy: string
  offlinePolicy: string
  createdAt: string
  updatedAt: string
}

/** 分页查询停车场列表 */
export function getParkingLots(params: { page: number; size: number; status?: string }) {
  return request.get<PageResult<ParkingLotVO>>('/admin/parking-lots', params)
}

/** 查询停车场详情 */
export function getParkingLot(id: number) {
  return request.get<ParkingLotVO>(`/admin/parking-lots/${id}`)
}

/** 创建停车场 */
export function createParkingLot(data: { name: string; totalSpaces: number; address?: string; contactPhone?: string }) {
  return request.post<ParkingLotVO>('/admin/parking-lots', data)
}

/** 更新停车场 */
export function updateParkingLot(id: number, data: Record<string, any>) {
  return request.put<ParkingLotVO>(`/admin/parking-lots/${id}`, data)
}

/** 启用/停用停车场 */
export function updateParkingLotStatus(id: number, data: { action: string; reason?: string }) {
  return request.post(`/admin/parking-lots/${id}/status`, data)
}

/** 修改总车位/剩余车位 */
export function updateParkingLotCapacity(id: number, data: { fieldName: string; value: number; reason: string }) {
  return request.post(`/admin/parking-lots/${id}/capacity`, data)
}
