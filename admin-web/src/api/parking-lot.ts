import request from '@/utils/request'
import type { PageResult } from '@/types'

/** 停车场视图 */
export interface ParkingLotVO {
  id: number
  tenantId: number
  companyId: number
  groupId: number
  name: string
  province: string
  city: string
  district: string
  regionType: number
  address: string
  longitude: string
  latitude: string
  contactName: string
  contactPhone: string
  status: number
  businessHours: string
  totalSpaces: number
  images: string
  version: number
  createdAt: string
  updatedAt: string
}

/** 区域类型选项 */
export const REGION_TYPE_OPTIONS = [
  { label: '商场', value: 1 },
  { label: '写字楼', value: 2 },
  { label: '住宅小区', value: 3 },
  { label: '医院', value: 4 },
  { label: '景区', value: 5 },
  { label: '交通枢纽', value: 6 },
]

/** 状态选项 */
export const PARKING_LOT_STATUS_OPTIONS = [
  { label: '营业中', value: 1 },
  { label: '暂停营业', value: 2 },
  { label: '装修升级', value: 3 },
]

/** 分页查询停车场列表 */
export function getParkingLots(params: {
  page?: number
  size?: number
  name?: string
  status?: number
}) {
  return request.get<PageResult<ParkingLotVO>>('/admin/parking-lots', params)
}

/** 查询停车场详情 */
export function getParkingLot(id: number) {
  return request.get<ParkingLotVO>(`/admin/parking-lots/${id}`)
}

/** 创建停车场 */
export function createParkingLot(data: {
  companyId: number
  groupId?: number
  name: string
  province?: string
  city?: string
  district?: string
  regionType?: number
  address?: string
  longitude?: string
  latitude?: string
  contactName?: string
  contactPhone?: string
  status?: number
  businessHours?: string
  totalSpaces?: number
  images?: string
}) {
  return request.post<ParkingLotVO>('/admin/parking-lots', data)
}

/** 更新停车场 */
export function updateParkingLot(id: number, data: Record<string, any>) {
  return request.put<ParkingLotVO>(`/admin/parking-lots/${id}`, data)
}

/** 删除停车场（软删除） */
export function deleteParkingLot(id: number) {
  return request.delete<void>(`/admin/parking-lots/${id}`)
}

/** 更新停车场状态 */
export function updateParkingLotStatus(id: number, status: number) {
  return request.post<void>(`/admin/parking-lots/${id}/status`, { action: status === 1 ? "ENABLED" : "DISABLED" })
}

/** 更新停车场图片 */
export function updateParkingLotImages(id: number, images: string[]) {
  return request.post<void>(`/admin/parking-lots/${id}/images`, images)
}
