import request from '@/utils/request'

/** 岗亭端车场信息 */
export interface BoothParkingLot {
  id: number
  name: string
  status: string
}

/**
 * 获取当前岗亭管理员被授权访问的车场列表。
 * GET /api/v1/booth/parking-lots
 */
export function getBoothParkingLots() {
  return request.get<BoothParkingLot[]>('/api/v1/booth/parking-lots')
}
