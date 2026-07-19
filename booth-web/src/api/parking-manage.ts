import request from '@/utils/request'

// ============ Company (for parking lot form) ============
export interface CompanyVO {
  id: number; name: string; tenantId?: number; level?: number
}

export function getCompanyTree(): Promise<CompanyVO[]> {
  return request.get('/admin/companies/tree')
}

// ============ Tenant (超管创建车场时选择归属租户) ============
export interface TenantVO {
  id: number; name: string; status?: string
}

export function getTenants(params: { page: number; size: number }) {
  return request.get<PageResult<TenantVO>>('/admin/tenants', params)
}

// ============ PageResult ============
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

// ============ Parking Lot (SA-01) ============
export interface ParkingLotVO {
  id: number; name: string; tenantId: number; tenantName: string
  address: string; totalSpaces: number; remainingSpaces: number
  status: string; createdAt: string
  contactName?: string; contactPhone?: string
}

export interface ParkingLotPageQuery {
  page: number; size: number; keyword?: string; status?: string
}

export function getParkingLots(params: ParkingLotPageQuery) {
  return request.get<PageResult<ParkingLotVO>>('/admin/parking-lots', params)
}

export interface ParkingLotCreateCmd {
  companyId?: number
  tenantId?: number
  name: string
  address?: string
  totalSpaces?: number
  contactPhone?: string
}

export interface ParkingLotUpdateCmd {
  name?: string
  address?: string
  totalSpaces?: number
}

export function createParkingLot(data: ParkingLotCreateCmd): Promise<ParkingLotVO> {
  return request.post('/admin/parking-lots', data)
}

export function updateParkingLot(id: number, data: ParkingLotUpdateCmd): Promise<ParkingLotVO> {
  return request.put(`/admin/parking-lots/${id}`, data)
}

export function deleteParkingLot(id: number): Promise<void> {
  return request.delete(`/admin/parking-lots/${id}`)
}

export function updateParkingLotStatus(id: number, action: 'ENABLED' | 'DISABLED'): Promise<void> {
  return request.post(`/admin/parking-lots/${id}/status`, { action })
}

// ============ Parking Lane (SA-02) ============
export interface ParkingLaneVO {
  id: number; name: string; laneNo: string; lotId: number; lotName: string
  type: number; typeLabel?: string; gateMode: string; gateModeLabel?: string
  status: number; createdAt: string
}

export interface LanePageQuery {
  page: number; size: number; parkingLotId?: number; status?: number; type?: number
}

export function getParkingLanes(params: LanePageQuery) {
  return request.get<PageResult<ParkingLaneVO>>('/admin/lanes', params)
}

export interface ParkingLaneCreateCmd {
  lotId: number
  name: string
  laneNo: string
  type: number
  gateMode?: string
}

export interface ParkingLaneUpdateCmd {
  name?: string
  laneNo?: string
  type?: number
  gateMode?: string
}

export function createParkingLane(data: ParkingLaneCreateCmd): Promise<ParkingLaneVO> {
  return request.post('/admin/lanes', data)
}

export function updateParkingLane(id: number, data: ParkingLaneUpdateCmd): Promise<ParkingLaneVO> {
  return request.put(`/admin/lanes/${id}`, data)
}

export function deleteParkingLane(id: number): Promise<void> {
  return request.delete(`/admin/lanes/${id}`)
}

export function updateParkingLaneStatus(id: number, action: 'ENABLED' | 'DISABLED'): Promise<void> {
  return request.post(`/admin/lanes/${id}/status`, { action })
}

// ============ Device (SA-03) ============
export interface DeviceVO {
  id: number; name: string; deviceSn: string; deviceType: string; deviceVendor: string
  laneId: number | null; laneName: string | null; parkingLotId: number; parkingLotName: string
  status: string; capabilities: string; createdAt: string
  code?: string; vendorId?: number; modelId?: number
  ipAddress?: string
  port?: number
  subnetMask?: string
  gateway?: string
}

export interface DevicePageQuery {
  page: number; size: number; parkingLotId?: number; status?: string; deviceType?: string
}

export function getDevices(params: DevicePageQuery) {
  return request.get<PageResult<DeviceVO>>('/admin/devices', params)
}

export interface DeviceVendor {
  id: number; name: string; code: string
}

export interface DeviceModel {
  id: number; vendorId: number; name: string; code: string; deviceType: string
}

export interface DeviceCreateCmd {
  parkingLotId: number
  vendorId: number
  modelId: number
  name: string
  code: string
  deviceSn: string
  deviceType: string
  laneId?: number | null
  ipAddress?: string
  port?: number
  subnetMask?: string
  gateway?: string
}

export interface DeviceUpdateCmd {
  name?: string
  code?: string
  deviceType?: string
  laneId?: number | null
  ipAddress?: string
  port?: number
  subnetMask?: string
  gateway?: string
}

export function createDevice(data: DeviceCreateCmd): Promise<DeviceVO> {
  return request.post('/admin/devices', data)
}

export function updateDevice(id: number, data: DeviceUpdateCmd): Promise<DeviceVO> {
  return request.put(`/admin/devices/${id}`, data)
}

export function updateDeviceStatus(id: number, action: 'ENABLED' | 'DISABLED'): Promise<void> {
  return request.post(`/admin/devices/${id}/status`, { action })
}

export function bindDeviceLane(deviceId: number, laneId: number): Promise<DeviceVO> {
  return request.post(`/admin/devices/${deviceId}/bind-lane`, { laneId })
}

export function unbindDeviceLane(deviceId: number): Promise<DeviceVO> {
  return request.delete(`/admin/devices/${deviceId}/bind-lane`)
}

export function getDeviceVendors(): Promise<DeviceVendor[]> {
  return request.get('/admin/devices/vendors')
}

export function getDeviceModels(vendorId?: number): Promise<DeviceModel[]> {
  return request.get('/admin/devices/models', vendorId ? { vendorId } : {})
}

// ============ Vehicle List (OP-01) ============
export interface VehicleListVO {
  id: number; plateNumber: string; listType: string; parkingLotId: number; parkingLotName: string
  triggerType: string | null; status: string; expireAt: string | null; createdAt: string
}

export interface VehicleListPageQuery {
  page: number; size: number; parkingLotId?: number; listType?: string; plateNumber?: string
}

export function getVehicleList(params: VehicleListPageQuery) {
  return request.get<PageResult<VehicleListVO>>('/v1/admin/vehicle-list/page', params)
}

export function deleteVehicle(id: number) {
  return request.delete(`/v1/admin/vehicle-list/${id}`)
}

export function importVehicles(file: File, parkingLotId: number) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<{ total: number; successCount: number; failCount: number; errors: any[] }>(`/v1/admin/vehicle-list/import?parkingLotId=${parkingLotId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ============ Dashboard (OP overview) ============
export interface DashboardStats {
  totalLots: number; totalLanes: number; totalDevices: number
  activeSessions: number; todayEntries: number; todayExits: number
}

export function getDashboardStats() {
  return request.get<DashboardStats>('/v1/admin/dashboard/overview')
}

// ============ Analytics (运营数据) ============
export interface TrendPoint {
  time: string
  entry: number
  exit: number
}

/** 营收汇总（单位：元） */
export interface RevenueStats {
  /** 实收总额：统计周期内已出场会话 paid_amount 汇总 */
  totalPaid: number
  /** 固定车营收：一期无固定车收费数据源，为 0 */
  fixedCarRevenue: number
  /** 其他营收 = totalPaid - fixedCarRevenue */
  otherRevenue: number
}

export interface AnalyticsOverviewVO {
  entryCount: number
  exitCount: number
  currentInCount: number
  entryTriggerStats: Record<string, number>
  trendData: TrendPoint[]
  revenue: RevenueStats | null
}

export interface AnalyticsQueryParams {
  lotId?: number
  period: 'today' | 'month' | 'year' | 'custom'
  startDate?: string
  endDate?: string
}

export function getAnalyticsOverview(params: AnalyticsQueryParams) {
  return request.get<AnalyticsOverviewVO>('/v1/admin/analytics/overview', params)
}
