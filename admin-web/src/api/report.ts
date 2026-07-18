import request from '@/utils/request'

export type ReportPeriod = 'DAILY' | 'MONTHLY' | 'YEARLY'

export interface RevenuePeriodStat {
  period: string
  totalRevenue: number
  orderCount: number
}

export interface RevenueReportVO {
  totalRevenue: number
  orderCount: number
  avgOrderAmount: number
  periods: RevenuePeriodStat[]
}

export interface TrafficDailyStat {
  date: string
  entryCount: number
  exitCount: number
}

export interface TrafficHourlyStat {
  hour: number
  entryCount: number
  exitCount: number
  total: number
}

export interface TrafficReportVO {
  totalEntry: number
  totalExit: number
  peakHour: number
  peakCount: number
  dailyStats: TrafficDailyStat[]
  hourlyStats: TrafficHourlyStat[]
}

/**
 * 收入报表（摘要 + 按期列表）。
 */
export function getRevenueReport(params: {
  periodType?: ReportPeriod
  startDate: string
  endDate: string
  lotId?: number
}) {
  return request.get<RevenueReportVO>('/v1/admin/reports/revenue', params)
}

/**
 * 车流量报表。
 */
export function getTrafficReport(params: {
  startDate: string
  endDate: string
  lotId?: number
  laneId?: number
}) {
  return request.get<TrafficReportVO>('/v1/admin/reports/traffic', params)
}

/**
 * 导出收入报表 xlsx。
 */
export function exportRevenueReport(params: {
  periodType?: ReportPeriod
  startDate: string
  endDate: string
  lotId?: number
}) {
  return request.get<Blob>('/v1/admin/reports/revenue/export', params, {
    responseType: 'blob',
  } as any)
}

/**
 * 导出车流量报表 xlsx。
 */
export function exportTrafficReport(params: {
  startDate: string
  endDate: string
  lotId?: number
}) {
  return request.get<Blob>('/v1/admin/reports/traffic/export', params, {
    responseType: 'blob',
  } as any)
}
