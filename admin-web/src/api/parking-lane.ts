import request from '@/utils/request'
import type { PageResult } from '@/types'
import type { DeviceVO } from './device'

/** 车道视图 */
export interface ParkingLaneVO {
  id: number
  parkingLotId: number
  name: string
  code: string
  direction: string
  status: string
  isKeyLane: number
  autoReleasePolicy: string
  description: string
  devices: DeviceVO[]
  createdAt: string
  updatedAt: string
}

/** 分页查询车道列表 */
export function getLanes(params: {
  page: number
  size: number
  parkingLotId?: number
  status?: string
  direction?: string
}) {
  return request.get<PageResult<ParkingLaneVO>>('/admin/lanes', { params })
}

/** 查询车道详情 */
export function getLane(id: number) {
  return request.get<ParkingLaneVO>(`/admin/lanes/${id}`)
}

/** 创建车道 */
export function createLane(data: {
  parkingLotId: number
  name: string
  code: string
  direction: string
  isKeyLane?: boolean
  autoReleasePolicy?: string
  description?: string
}) {
  return request.post<ParkingLaneVO>('/admin/lanes', data)
}

/** 更新车道 */
export function updateLane(id: number, data: Record<string, any>) {
  return request.put<ParkingLaneVO>(`/admin/lanes/${id}`, data)
}

/** 启用/停用车道 */
export function updateLaneStatus(id: number, action: string) {
  return request.post(`/admin/lanes/${id}/status`, { action })
}
