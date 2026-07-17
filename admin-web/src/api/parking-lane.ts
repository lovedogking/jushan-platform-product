import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 通道视图 */
export interface ParkingLaneVO {
  id: number
  tenantId: number
  lotId: number
  zoneId: number
  laneNo: string
  name: string
  type: number
  entryCameraId: number
  exitCameraId: number
  status: number
  tideMode: number
  cameraMode: number
  version: number
  createdAt: string
  updatedAt: string
}

/** 通道类型选项 */
export const LANE_TYPE_OPTIONS = [
  { label: '入口', value: 1 },
  { label: '出口', value: 2 },
  { label: '双向', value: 3 },
]

/** 通道状态选项 */
export const LANE_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 2 },
  { label: '维护中', value: 3 },
]

/** 潮汐模式选项 */
export const TIDE_MODE_OPTIONS = [
  { label: '关闭', value: 0 },
  { label: '早高峰入口', value: 1 },
  { label: '晚高峰出口', value: 2 },
]

/** 相机模式选项 */
export const CAMERA_MODE_OPTIONS = [
  { label: '单相机', value: 1 },
  { label: '双相机', value: 2 },
  { label: '主从相机', value: 3 },
]

/** 分页查询通道列表 */
export function getParkingLanes(params: {
  page?: number
  size?: number
  parkingLotId?: number
  zoneId?: number
  type?: number
  status?: number
}) {
  return request.get<PageResult<ParkingLaneVO>>('/admin/lanes', params)
}

/** 查询通道详情 */
export function getParkingLane(id: number) {
  return request.get<ParkingLaneVO>(`/admin/lanes/${id}`)
}

/** 创建通道 */
export function createParkingLane(data: {
  lotId: number
  zoneId?: number
  laneNo: string
  name: string
  type?: number
  entryCameraId?: number
  exitCameraId?: number
  status?: number
  tideMode?: number
  cameraMode?: number
}) {
  return request.post<ParkingLaneVO>('/admin/lanes', data)
}

/** 更新通道 */
export function updateParkingLane(id: number, data: Record<string, any>) {
  return request.put<ParkingLaneVO>(`/admin/lanes/${id}`, data)
}

/** 删除通道（软删除） */
export function deleteParkingLane(id: number) {
  return request.delete<void>(`/admin/lanes/${id}`)
}

/** 更新通道状态 */
export function updateParkingLaneStatus(id: number, status: number) {
  return request.post<void>(`/admin/lanes/${id}/status`, { action: status === 1 ? "ENABLED" : "DISABLED" })
}
