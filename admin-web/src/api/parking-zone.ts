import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 区域视图 */
export interface ParkingZoneVO {
  id: number
  tenantId: number
  lotId: number
  name: string
  tag: string
  level: number
  feeRuleId: number
  totalSpaces: number
  fixedSpaces: number
  tempSpaces: number
  status: number
  managerId: number
  remark: string
  version: number
  createdAt: string
  updatedAt: string
}

/** 区域等级选项 */
export const ZONE_LEVEL_OPTIONS = [
  { label: '普通', value: 1 },
  { label: 'VIP', value: 2 },
  { label: '员工', value: 3 },
]

/** 区域标签选项 */
export const ZONE_TAG_OPTIONS = [
  { label: '普通', value: 'NORMAL' },
  { label: 'VIP', value: 'VIP' },
  { label: '员工', value: 'EMPLOYEE' },
  { label: '装卸', value: 'LOADING' },
  { label: '充电', value: 'CHARGE' },
]

/** 区域状态选项 */
export const ZONE_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 2 },
]

/** 分页查询区域列表 */
export function getParkingZones(params: {
  current?: number
  size?: number
  lotId?: number
  status?: number
}) {
  return request.get<PageResult<ParkingZoneVO>>('/api/v1/parking-zones', params)
}

/** 按车场查询区域列表 */
export function getParkingZonesByLotId(lotId: number) {
  return request.get<ParkingZoneVO[]>(`/api/v1/parking-zones/by-lot/${lotId}`)
}

/** 查询区域详情 */
export function getParkingZone(id: number) {
  return request.get<ParkingZoneVO>(`/api/v1/parking-zones/${id}`)
}

/** 创建区域 */
export function createParkingZone(data: {
  lotId: number
  name: string
  tag?: string
  level?: number
  feeRuleId?: number
  totalSpaces?: number
  fixedSpaces?: number
  status?: number
  managerId?: number
  remark?: string
}) {
  return request.post<ParkingZoneVO>('/api/v1/parking-zones', data)
}

/** 更新区域 */
export function updateParkingZone(id: number, data: Record<string, any>) {
  return request.put<ParkingZoneVO>(`/api/v1/parking-zones/${id}`, data)
}

/** 删除区域（软删除） */
export function deleteParkingZone(id: number) {
  return request.delete<void>(`/api/v1/parking-zones/${id}`)
}

/** 更新区域状态 */
export function updateParkingZoneStatus(id: number, status: number) {
  return request.post<void>(`/api/v1/parking-zones/${id}/status`, undefined, { params: { status } })
}
