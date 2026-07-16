import request from '@/utils/request'

/** 仪表盘 VO */
export interface DashboardVO {
  todayRevenue: number
  todayTraffic: {
    entryCount: number
    exitCount: number
    total: number
  }
  currentParkedCount: number
  remainingSpaces: number
  deviceStatus: {
    online: number
    offline: number
    total: number
  }
  unhandledAlertCount: number
  hourlyRevenue: { hour: number; amount: number }[]
  hourlyTraffic: { hour: number; entryCount: number; exitCount: number }[]
}

/**
 * 获取仪表盘首页数据
 */
export function getDashboard() {
  return request.get<DashboardVO>('/v1/admin/dashboard')
}
